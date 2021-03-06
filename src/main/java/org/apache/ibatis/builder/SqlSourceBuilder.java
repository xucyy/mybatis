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
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * todo  在经过SqlNode.apply()方法的解析之后，Sql语句会被传递到SqlSourceBuilder中进行下一步的解析
 *   1）一方面解析SQL语句中的 #{}占位符中定义的属性，格式类似于#{__frc_item_0,javaType=int,jdbcType=NUMERIC，typeHandler=MyTypeHandler}
 *   2) 另外一方面是将SQL语句中的 #{} 占位符替换成"?" 占位符
 */
public class SqlSourceBuilder extends BaseBuilder {

  private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }

  //todo  三个步骤
  // 1）第一个参数是经过 SqlNode.apply()方法处理之后的SQL语句
  // 2）第二个参数是用户传入的实参类型
  // 3）第三个参数记录了形参的对应关系，试试就是经过SqlNode.apply()方法处理后的
  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
    //todo 创建ParameterMappingTokenHandler 对象，他是解析#{} 占位符中的参数属性以及替换占位符的核心
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
    //todo 使用GenericTokenParser和ParameterMappingTokenHandler配合解析 #{}占位符
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);
    String sql = parser.parse(originalSql);
    //todo 创建StaticSqlSource ，其中封装了占位符被替换成"?" 的SQL语句以及参数对应的 ParameterMapping集合
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }

  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {

    //todo 用于记录解析得到的ParameterMapping集合
    private List<ParameterMapping> parameterMappings = new ArrayList<>();
    //todo 参数类型
    private Class<?> parameterType;
    //todo DynamicContext.bindings集合对应的MetaObject对象
    private MetaObject metaParameters;

    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }

    //todo 调用buildParameterMapping()方法解析参数属性，并将解析得到的ParameterMapping对象添加到parameterMappings集合中
    @Override
    public String handleToken(String content) {
      //todo 创建一个ParameterMapping 对象，并添加到 parameterMappings集合中
      parameterMappings.add(buildParameterMapping(content));
      //todo 返回问好占位符
      return "?";
    }
    //todo 负责解析参数属性，例如 #{__frc_item_0,javaType=int,jdbcType=NUMERIC,typeHandler=MyTypeHandler}这个占位符
    // 他会被解析成如下map:{"property"->"__frm_item_0","javaType"->"int","jdbcType"->"NUMRIC"}
    private ParameterMapping buildParameterMapping(String content) {
      Map<String, String> propertiesMap = parseParameterMapping(content);
      //todo 获取参数名称
      String property = propertiesMap.get("property");
      //todo 确定参数javaType属性
      Class<?> propertyType;
      if (metaParameters.hasGetter(property)) { // issue #448 get type from additional params
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        propertyType = java.sql.ResultSet.class;
      } else if (property == null || Map.class.isAssignableFrom(parameterType)) {
        propertyType = Object.class;
      } else {
        MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());
        if (metaClass.hasGetter(property)) {
          propertyType = metaClass.getGetterType(property);
        } else {
          propertyType = Object.class;
        }
      }
      //todo 创建ParameterMapping的建造者，并设置ParameterMapping相关配置
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if ("javaType".equals(name)) {
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          builder.jdbcType(resolveJdbcType(value));
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + PARAMETER_PROPERTIES);
        }
      }
      if (typeHandlerAlias != null) {
        //todo 获取typeHandler对象
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }
      //todo 创建ParameterMapping对象，注意如果没有指定TypeHandler，则会在这里的build中根据javaType和JdbcType从
      //  TypeHandlerRegistry中获取相应的TypeHandler对象
      return builder.build();
    }

    private Map<String, String> parseParameterMapping(String content) {
      try {
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }

}
