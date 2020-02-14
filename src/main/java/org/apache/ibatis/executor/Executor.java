/**
 *    Copyright 2009-2015 the original author or authors.
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

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Clinton Begin
 * todo 其中定义了数据库操作的基本方法，在实际应用中经常涉及到SqlSession接口的功能，都是基于Executor接口实现的。
 */
public interface Executor {

  ResultHandler NO_RESULT_HANDLER = null;
  //todo 执行update ,insert,delete三种类型的SQL语句
  int update(MappedStatement ms, Object parameter) throws SQLException;

  //todo 执行select类型的SQL语句，返回值为 对象列表
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException;

  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException;

  //todo 执行select类型的SQL语句，返回值为 游标
  <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException;

  //todo 批量执行SQL语句
  List<BatchResult> flushStatements() throws SQLException;

  //todo 提交事务
  void commit(boolean required) throws SQLException;

  //todo 回滚事务
  void rollback(boolean required) throws SQLException;

  //todo 创建缓存中用到的CacheKey对象
  CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);

  //todo 根据CacheKey对象查找缓存
  boolean isCached(MappedStatement ms, CacheKey key);

  //todo 清空一级缓存
  void clearLocalCache();

  //todo 延迟加载一级缓存中的数据
  void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType);

  //todo 获取事务对象
  Transaction getTransaction();

  //todo 关闭Executor对象
  void close(boolean forceRollback);
  //todo 检测Executor是否已关闭
  boolean isClosed();

  void setExecutorWrapper(Executor executor);

}
