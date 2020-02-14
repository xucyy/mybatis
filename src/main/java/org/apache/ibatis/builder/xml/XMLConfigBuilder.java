/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 * todo 解析mybatis-config.xml文件
 */
public class XMLConfigBuilder extends BaseBuilder {

  //todo 标识是否已经解析过mybatis-config.xml配置文件
  private boolean parsed;
  //todo 用于解析mybatis-config.xml配置文件的XPathParser对象
  private final XPathParser parser;
  //todo 标识<environment>配置的名称，默认读取<environment>标签的default属性
  private String environment;
  //todo 负责创建和缓存Reflector对象
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    //todo 传入新创建的Configuration对象，全局唯一
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  public Configuration parse() {
    //todo 如果已经解析过了 ，就报错
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    //todo 设置已经解析了
    parsed = true;
    //TODO 解析标签，从/configuration开始解析
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      // TODO 首先解析的就是properties标签，引入外部配置文件的信息，提供给后面的标签使用。这也是为什么在xml文件中properties标签要放在前面的原因，因为需要最先解析
      propertiesElement(root.evalNode("properties"));
      //todo 解析<settings> 节点
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      //todo 解析VfsImpl字段
      loadCustomVfs(settings);
      loadCustomLogImpl(settings);
      typeAliasesElement(root.evalNode("typeAliases")); //TODO 解析别名标签，别名用于配置resultType的类型
      pluginElement(root.evalNode("plugins")); //TODO  插件标签，用于实现一些日志打印，方法拦截，就跟过滤器似的
      objectFactoryElement(root.evalNode("objectFactory"));//todo 解析<objectFactory> 节点
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));//todo 解析<objectWrapperFactory>节点
      reflectorFactoryElement(root.evalNode("reflectorFactory"));//todo  解析reflectorFactory节点
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631 TODO  解析数据源标签
      environmentsElement(root.evalNode("environments"));
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));//todo 解析<databaseIdProvider>节点
      typeHandlerElement(root.evalNode("typeHandlers")); //TODO  用于数据库类型和java类转换
      mapperElement(root.evalNode("mappers")); //TODO  解析配置的mapper文件
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }
  //todo 解析<settings> 节点，在settings节点下的配置是mybatis全局性的配置，它们会改变mybatis的运行时行为
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    //todo 得到<settings> 下的所有<setting>配置项
    Properties props = context.getChildrenAsProperties();
    //todo 使用MetaClass检测 key指定的属性在Configuration类中是否有对应setter方法
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      //todo 检查 Configuration类中是否有对应的setter方法
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  //todo 解析<typeAliases> 节点，并通过 typeAliasRegistry完成别名的注册
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      //todo 处理全部子节点
      for (XNode child : parent.getChildren()) {
        //todo 处理<package>节点
        if ("package".equals(child.getName())) {
          //todo 获取指定的包名
          String typeAliasPackage = child.getStringAttribute("name");
          //todo 通过TypeAliasRegistry扫描包下所有的类，并解析@Alias注解，完成别名注册
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          //todo 处理<typeAlias>节点
          String alias = child.getStringAttribute("alias");//todo 获取指定的别名
          String type = child.getStringAttribute("type");//todo 获取别名对应的类型
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              //todo 扫描@Alias注解 ，完成注册
              typeAliasRegistry.registerAlias(clazz);
            } else {
              //todo 注册别名
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }
  //todo 解析<plugins>节点
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      //todo 循环获取<plugin>节点
      for (XNode child : parent.getChildren()) {
        //TODO  解析拦截器标签
        String interceptor = child.getStringAttribute("interceptor");//todo 获取<plugin>节点的interceptor属性的值
        //todo 读取<plugin> 节点下<properties>配置的信息，并形成Properties对象
        Properties properties = child.getChildrenAsProperties();
        //todo 根据前面的typeAliasRegistry 解析别名之后，实例化Interceptor
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
        interceptorInstance.setProperties(properties);
        //TODO  放入interceptorChain 拦截器链中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }
  //todo 负责解析objectFactory节点，并实例化指定的objectFactory实现类，之后将自定义的ObjectFactory对象记录到Configuration.objectFactory中
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }
  //todo 负责解析objectWrapperFactory节点，并实例化指定的objectWrapperFactory实现类，之后将自定义的ObjectFactory对象记录到Configuration.objectWrapperFactory中
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }
  //todo 负责解析reflectorFactory节点，并实例化指定的reflectorFactory实现类，之后将自定义的reflectorFactory对象记录到Configuration.reflectorFactory中
  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setReflectorFactory(factory);
    }
  }

  //todo 解析<properties> 节点，并生成java.util.Properties对象，
  // 之后将Properties对象设置到XPathParser和Configuration的variables字段中
  // 后续会使用该Properties对象中的信息替换占位符
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      //todo 解析<properties>的子节点<Property>标签的 name和value属性，并记录到Properties中
      Properties defaults = context.getChildrenAsProperties();
      //todo 解析<properties>的resource和url属性，这两个属性用于确定properties配置文件的位置
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      //todo resource和url都存在，报错
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      //todo 根据配置文件资源来添加配置信息
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      //todo 获取configuration中的 variables集合 进行合并
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      //todo 更新XPathParser和Configuration的variables字段
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  //todo 设置<settings>下解析出来的全局配置项
  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  //todo 解析环境变量，可以根据不同的开发，测试，生产环境配置不同的环境变量
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      //todo 未指定XMLConfigBuilder.environment字段 ,则使用default属性指定的 <environment>
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      //todo 遍历子节点（即<environment>节点）
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        //todo 与XMLConfigBuilder.environment字段比较，相等的话，就进行解析数据源
        if (isSpecifiedEnvironment(id)) {
          //todo 创建TransactionFactory,具体实现是先通过TypeAliasRegistry解析别名之后，实例化TransactionFactory
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          //TODO 解析数据源，存到environment中
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          //todo 获取数据源
          DataSource dataSource = dsFactory.getDataSource();
          //todo 利用建造者模式，创建由 dataSource和 transactionFactory构建的 Environment
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }
  //todo 解析 <databaseIdProvider>节点 。可以在这个标签下定义所有支持的数据库产品的databaseId,然后在映射配置文件中定义SQL语句节点时，通过
  //  databaseId指定该SQL语句应用的数据库产品，这样也可以实现帮助开发人员屏蔽多种数据库的差异
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      //todo 根据type属性 ，创建指定的DatabaseIdProvider对象
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
      databaseIdProvider.setProperties(properties);
    }
    //todo 解析driver和databaseId ,指定configuration的 databaseId属性
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }
  //todo 解析Environments 下的transactionFactory标签
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    //todo transactionFactory标签不为空
    if (context != null) {
      //todo 解析type属性
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      //todo 使用typeAliasRegistry解析别名后 实例化TransactionFactroy
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  //todo 解析dataSource
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      //todo 根据type属性不同
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      //todo 创建不同类型的DataSourceFactory 比如type=Pooled 则创建PooledDataSourceFactory
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  //todo 解析typeHandler节点，并通过typeHandlerRegistry来进行注册
  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      //todo 循环所有的子节点
      for (XNode child : parent.getChildren()) {
        //todo 根据包名来注册typeHandler
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          //todo 解析typeHandler
          String javaTypeName = child.getStringAttribute("javaType");//todo 获取javaType
          String jdbcTypeName = child.getStringAttribute("jdbcType");//todo 获取jdbcType
          String handlerTypeName = child.getStringAttribute("handler");//todo 获取handler
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          //todo  通过typeHandler进行注册
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  //todo 解析mapper节点
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      //todo 处理<mappers>的子节点
      for (XNode child : parent.getChildren()) {
        //TODO  mapper标签的配置有4种 package url resource class
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
          //todo 扫描指定的包，并向mapperRegistry注册Mapper接口
          configuration.addMappers(mapperPackage);
        } else {
          //todo resource url class 这三个属性互斥，不可同时两两存在
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            //TODO  解析mapper配置的是resource
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            //todo 创建XMLMapperBuilder 来解析指定的Mapper配置文件
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            //TODO  解析mapper配置的是url
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            //todo 创建XMLMapperBuilder 来解析指定的Mapper配置文件
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            //TODO  解析mapper配置的是class
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            //todo 向MapperRegistry注册mapper
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }
  //todo 比较配置的environment的id  和environment是否相等，相等就说明找到了对应的数据源
  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
