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
package org.apache.ibatis.executor.loader;

import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.ResultExtractor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;

/**
 * @author Clinton Begin
 * todo 主要负责保存一次延迟加载操作所需的全部信息
 */
public class ResultLoader {

  protected final Configuration configuration;
  protected final Executor executor;
  //todo 记录延迟加载的SQL节点信息
  protected final MappedStatement mappedStatement;
  //todo 记录延迟加载的SQL语句 实参
  protected final Object parameterObject;
  //todo 记录了延迟加载得到的对象类型
  protected final Class<?> targetType;
  protected final ObjectFactory objectFactory;
  protected final CacheKey cacheKey;
  //todo 记录了延迟执行的SQL语句
  protected final BoundSql boundSql;
  //todo 负责将延迟加载得到的结果对象转换成targetType类型的对象
  protected final ResultExtractor resultExtractor;
  //todo 创建ResultLoader的线程id
  protected final long creatorThreadId;

  protected boolean loaded;
  //todo 记录加载得到的结果集
  protected Object resultObject;

  public ResultLoader(Configuration config, Executor executor, MappedStatement mappedStatement, Object parameterObject, Class<?> targetType, CacheKey cacheKey, BoundSql boundSql) {
    this.configuration = config;
    this.executor = executor;
    this.mappedStatement = mappedStatement;
    this.parameterObject = parameterObject;
    this.targetType = targetType;
    this.objectFactory = configuration.getObjectFactory();
    this.cacheKey = cacheKey;
    this.boundSql = boundSql;
    this.resultExtractor = new ResultExtractor(configuration, objectFactory);
    this.creatorThreadId = Thread.currentThread().getId();
  }
  //todo  该方法会通过executor执行resultLoader中记录的SQL语句并返回相应的延迟加载对象
  public Object loadResult() throws SQLException {
    //todo 执行延迟加载，得到结果对象，并以list的形式返回
    List<Object> list = selectList();
    //todo 将list集合转换成targetType指定类型的对象
    resultObject = resultExtractor.extractObjectFromList(list, targetType);
    return resultObject;
  }
  //todo 具体完成延迟加载操作的地方
  private <E> List<E> selectList() throws SQLException {
    //todo 记录执行延迟加载的executor对象
    Executor localExecutor = executor;
    //todo 检测调用该方法的线程是否为创建 ResultLoader对象的线程，检测localExecutor是否关闭，如果 为是 ，则创建新的executor来执行延迟加载
    if (Thread.currentThread().getId() != this.creatorThreadId || localExecutor.isClosed()) {
      localExecutor = newExecutor();
    }
    try {
      //todo 执行查询操作，得到延迟加载的对象
      return localExecutor.query(mappedStatement, parameterObject, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER, cacheKey, boundSql);
    } finally {
      //todo 如果是新的executor，则需要关闭
      if (localExecutor != executor) {
        localExecutor.close(false);
      }
    }
  }

  private Executor newExecutor() {
    final Environment environment = configuration.getEnvironment();
    if (environment == null) {
      throw new ExecutorException("ResultLoader could not load lazily.  Environment was not configured.");
    }
    final DataSource ds = environment.getDataSource();
    if (ds == null) {
      throw new ExecutorException("ResultLoader could not load lazily.  DataSource was not configured.");
    }
    final TransactionFactory transactionFactory = environment.getTransactionFactory();
    final Transaction tx = transactionFactory.newTransaction(ds, null, false);
    return configuration.newExecutor(tx, ExecutorType.SIMPLE);
  }

  public boolean wasNull() {
    return resultObject == null;
  }

}
