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
package org.apache.ibatis.executor;

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * todo 抽象模版类 主要提供缓存管理和事务管理的基本功能。  继承BaseExecutor的子类只要实现4个基本方法来完成数据库的相关操作即可 doUpdate doQuery doQueryCursor doFlushStatement
 */
public abstract class BaseExecutor implements Executor {

  private static final Log log = LogFactory.getLog(BaseExecutor.class);

  //todo Transaction 对象，实现事务的提交，回滚和关闭操作
  protected Transaction transaction;
  //todo 其中封装的executor
  protected Executor wrapper;
  //todo 延迟加载队列
  protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;
  //todo 一级缓存，用于缓存该Executor对象查询结果映射得到的结果对象
  protected PerpetualCache localCache;
  //todo 一级缓存，用于缓存输出类型的参数
  protected PerpetualCache localOutputParameterCache;
  protected Configuration configuration;

  //todo  用来记录嵌套查询的层数
  protected int queryStack;
  private boolean closed;

  protected BaseExecutor(Configuration configuration, Transaction transaction) {
    this.transaction = transaction;
    this.deferredLoads = new ConcurrentLinkedQueue<>();
    this.localCache = new PerpetualCache("LocalCache");
    this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
    this.closed = false;
    this.configuration = configuration;
    this.wrapper = this;
  }

  @Override
  public Transaction getTransaction() {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return transaction;
  }

  @Override
  public void close(boolean forceRollback) {
    try {
      try {
        //todo 首先先忽略缓存的SQL语句
        rollback(forceRollback);
      } finally {
        if (transaction != null) {
          //todo 关闭事务，也就是关闭Connection
          transaction.close();
        }
      }
    } catch (SQLException e) {
      // Ignore.  There's nothing that can be done at this point.
      log.warn("Unexpected exception on closing transaction.  Cause: " + e);
    } finally {
      transaction = null;
      deferredLoads = null;
      localCache = null;
      localOutputParameterCache = null;
      closed = true;
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public int update(MappedStatement ms, Object parameter) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    //todo 在调用doUpdate方法前，先清空缓存
    clearLocalCache();
    return doUpdate(ms, parameter);
  }

  //todo 主要是针对批处理多条SQL语句的
  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return flushStatements(false);
  }

  //todo isRollBack 为true表示不执行，为false表示执行也就是提交多条批量sql
  public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return doFlushStatements(isRollBack);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    //todo 获取BoundSql对象
    BoundSql boundSql = ms.getBoundSql(parameter);
    //TODO  创建缓存key,根据sql和参数
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
    //TODO  查询
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
    //todo 检测executor是否已经关闭
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    if (queryStack == 0 && ms.isFlushCacheRequired()) {
      //todo 非嵌套查询，并且<select> 节点配置的flushCache属性为true时，才会清空一级缓存，flushCache是影响一级缓存中结果对象存活时长的第一个方面
      clearLocalCache();
    }
    List<E> list;
    try {
      //todo 增加查询层数
      queryStack++;
      //TODO  查询一级缓存中是否存在key 也就是sql加参数 ，一级缓存的作用域只在当前session中
      list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
      if (list != null) {
        //TODO 针对存储过程调用的处理，其功能是：在一级缓存命中时，获取缓存中保存的输出类型参数，并设置到用户传入的实参（parameter）对象中
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
      } else {
        //TODO  不存在，去查询数据库
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }
    } finally {
      //todo 当前查询完成，查询层数减少
      queryStack--;
    }
    //todo 当最外层查询结束之后，就相当于所有的嵌套查询也已经完全加载完毕，这里开始触发DeferredLoad加载一级缓存中记录的嵌套查询的结果对象
    if (queryStack == 0) {
      //todo 触发DeferredLoad加载一级缓存中记录的嵌套查询的结果对象
      for (DeferredLoad deferredLoad : deferredLoads) {
        //todo 延迟加载的相关
        deferredLoad.load();
      }
      // todo 加载完成之后清空
      deferredLoads.clear();
      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
        // todo 根据localCacheScope配置决定是否清空一级缓存，localCacheShope配置是影响一级缓存中结果对象存活时长的第二个方面
        clearLocalCache();
      }
    }
    return list;
  }

  //todo 把结果封装成Cursor对并返回，待用户遍历完Cursor之后才真正的完成结果集的映射操作，并不会使用一级缓存
  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    return doQueryCursor(ms, parameter, rowBounds, boundSql);
  }

  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    //todo 创建DeferredLoad对象
    DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
    if (deferredLoad.canLoad()) {
      //todo 一级缓存中已经记录了指定查询的结果对象，直接从缓存中加载对象，并设置到外层对象中
      deferredLoad.load();
    } else {
      //todo 将Deferred对象添加到deferredLoads队列中，待整个外层查询结束后，再加载该结果对象
      deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
    }
  }

  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    //todo 检测当前Executor是否已经关闭
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    //TODO cacheKey的组成   1）MappedStatement的id 和2）sql的offset,limit  ,3）加上sql语句 在加上4）参数 5）最后加上数据源id
    CacheKey cacheKey = new CacheKey();
    cacheKey.update(ms.getId());
    cacheKey.update(rowBounds.getOffset());
    cacheKey.update(rowBounds.getLimit());
    cacheKey.update(boundSql.getSql());
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
    // todo 获取用户传入的实参，并添加到CacheKey对象中
    for (ParameterMapping parameterMapping : parameterMappings) {
      //todo 过滤掉输出类型的参数
      if (parameterMapping.getMode() != ParameterMode.OUT) {
        Object value;
        String propertyName = parameterMapping.getProperty();
        if (boundSql.hasAdditionalParameter(propertyName)) {
          value = boundSql.getAdditionalParameter(propertyName);
        } else if (parameterObject == null) {
          value = null;
        } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
          value = parameterObject;
        } else {
          MetaObject metaObject = configuration.newMetaObject(parameterObject);
          value = metaObject.getValue(propertyName);
        }
        //todo 将实参添加到CacheKey对象中
        cacheKey.update(value);
      }
    }
    if (configuration.getEnvironment() != null) {
      // todo 如果Environment的id不为空，则添加到CacheKey中
      cacheKey.update(configuration.getEnvironment().getId());
    }
    return cacheKey;
  }

  //todo 用来检测是否缓存了指定查询的结果对象
  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return localCache.getObject(key) != null;
  }

  @Override
  public void commit(boolean required) throws SQLException {
    if (closed) {
      throw new ExecutorException("Cannot commit, transaction is already closed");
    }
    //todo 清空一级缓存
    clearLocalCache();
    flushStatements();
    if (required) {
      transaction.commit();
    }
  }

  @Override
  public void rollback(boolean required) throws SQLException {
    if (!closed) {
      try {
        //todo 清空一级缓存
        clearLocalCache();
        flushStatements(true);
      } finally {
        if (required) {
          transaction.rollback();
        }
      }
    }
  }

  @Override
  public void clearLocalCache() {
    if (!closed) {
      localCache.clear();
      localOutputParameterCache.clear();
    }
  }

  protected abstract int doUpdate(MappedStatement ms, Object parameter)
      throws SQLException;

  protected abstract List<BatchResult> doFlushStatements(boolean isRollback)
      throws SQLException;

  protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
      throws SQLException;

  protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql)
      throws SQLException;

  protected void closeStatement(Statement statement) {
    if (statement != null) {
      try {
        statement.close();
      } catch (SQLException e) {
        // ignore
      }
    }
  }

  /**
   * Apply a transaction timeout.
   * @param statement a current statement
   * @throws SQLException if a database access error occurs, this method is called on a closed <code>Statement</code>
   * @since 3.4.0
   * @see StatementUtil#applyTransactionTimeout(Statement, Integer, Integer)
   */
  protected void applyTransactionTimeout(Statement statement) throws SQLException {
    StatementUtil.applyTransactionTimeout(statement, statement.getQueryTimeout(), transaction.getTimeout());
  }

  private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter, BoundSql boundSql) {
    if (ms.getStatementType() == StatementType.CALLABLE) {
      final Object cachedParameter = localOutputParameterCache.getObject(key);
      if (cachedParameter != null && parameter != null) {
        final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
        final MetaObject metaParameter = configuration.newMetaObject(parameter);
        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
          if (parameterMapping.getMode() != ParameterMode.IN) {
            final String parameterName = parameterMapping.getProperty();
            final Object cachedValue = metaCachedParameter.getValue(parameterName);
            metaParameter.setValue(parameterName, cachedValue);
          }
        }
      }
    }
  }
  //todo 从数据库中查询结果
  private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list;
    //todo 先在缓存中添加占位符
    localCache.putObject(key, EXECUTION_PLACEHOLDER);
    try {
      //todo 调用doQuery方法完成查询操作
      list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
    } finally {
      //todo 删除占位符
      localCache.removeObject(key);
    }
    //todo 将真正的结果对象添加到一级缓存中
    localCache.putObject(key, list);
    //todo 判断是不是存储过程调用
    if (ms.getStatementType() == StatementType.CALLABLE) {
      //todo 缓存输出类型的参数
      localOutputParameterCache.putObject(key, parameter);
    }
    return list;
  }

  protected Connection getConnection(Log statementLog) throws SQLException {
    Connection connection = transaction.getConnection();
    //todo 根据是否开启了debug模式，返回不同的对象
    if (statementLog.isDebugEnabled()) {
      //todo 返回Connection的动态代理对象
      return ConnectionLogger.newInstance(connection, statementLog, queryStack);
    } else {
      return connection;
    }
  }

  @Override
  public void setExecutorWrapper(Executor wrapper) {
    this.wrapper = wrapper;
  }

  //todo 负责从loaclCache缓存中延迟加载结果对象
  private static class DeferredLoad {

    //todo 外层对象对应的MetaObject对象
    private final MetaObject resultObject;
    //todo 延迟加载的属性名称
    private final String property;
    //todo 延迟加载的属性的类型
    private final Class<?> targetType;
    //todo 延迟加载的结果对象再一级缓存中相应的CacheKey对象
    private final CacheKey key;
    //todo 一级缓存，同BaseExecutor中的localCache指向同一个PerpetualCache对象
    private final PerpetualCache localCache;
    private final ObjectFactory objectFactory;
    //todo 负责结果对象的类型转换
    private final ResultExtractor resultExtractor;

    // issue #781
    public DeferredLoad(MetaObject resultObject,
                        String property,
                        CacheKey key,
                        PerpetualCache localCache,
                        Configuration configuration,
                        Class<?> targetType) {
      this.resultObject = resultObject;
      this.property = property;
      this.key = key;
      this.localCache = localCache;
      this.objectFactory = configuration.getObjectFactory();
      this.resultExtractor = new ResultExtractor(configuration, objectFactory);
      this.targetType = targetType;
    }

    //todo 负责检测缓存项是否已经完全加载到了缓存中
    //  完全加载就是说：BaseExecutor.queryFromDatabase()方法中，开始查询调用doQuery()方法查询数据库之前
    //  会先在localCache中添加占位符，带查询完成之后，才将真正的结果放入到localCache中缓存，此时该缓存才算完全加载
    public boolean canLoad() {
      return localCache.getObject(key) != null && localCache.getObject(key) != EXECUTION_PLACEHOLDER;
    }

    //todo 从缓存中加载结果对象，并设置到外层对象的相应属性中
    public void load() {
      @SuppressWarnings("unchecked")
      // todo 从缓存中查询指定的结果对象
      List<Object> list = (List<Object>) localCache.getObject(key);
      //todo 将缓存的结果对象转换成指定类型
      Object value = resultExtractor.extractObjectFromList(list, targetType);
      //todo 设置到外层对象的对应属性
      resultObject.setValue(property, value);
    }

  }

}
