/**
 *    Copyright 2009-2018 the original author or authors.
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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * @author Clinton Begin
 * todo 继承于BaseStatementHandler ，它底层使用java.sql.statement对象来完成数据库的相关操作，所以Sql语句中不能存在占位符
 *
 */
public class SimpleStatementHandler extends BaseStatementHandler {

  public SimpleStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  //todo 负责执行 insert,update或delete等类型的Sql语句，并且根据配置的KeyGenerator获取数据库生成的主键
  @Override
  public int update(Statement statement) throws SQLException {
    //todo 获取sql语句
    String sql = boundSql.getSql();
    //todo 获取用户传入的实参
    Object parameterObject = boundSql.getParameterObject();
    //todo 获取主键生成器对象
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    int rows;
    if (keyGenerator instanceof Jdbc3KeyGenerator) {
      //todo 执行SQL语句
      statement.execute(sql, Statement.RETURN_GENERATED_KEYS);
      //todo 获取受影响的行数
      rows = statement.getUpdateCount();
      //todo 将数据库生成的主键添加到parameterObject中
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else if (keyGenerator instanceof SelectKeyGenerator) {
      //todo 执行sql语句
      statement.execute(sql);
      //todo 获取收到影响的行数
      rows = statement.getUpdateCount();
      //todo 执行<selectKey>节点配置的SQL语句获取数据库生成的主键，并添加到parameterObject中
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    } else {
      statement.execute(sql);
      rows = statement.getUpdateCount();
    }
    return rows;
  }

  @Override
  public void batch(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    statement.addBatch(sql);
  }

  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    //todo 获取SQL语句
    String sql = boundSql.getSql();
    //todo 调用statement.execute方法执行Sql语句
    statement.execute(sql);
    //todo 映射结果集
    return resultSetHandler.handleResultSets(statement);
  }

  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    statement.execute(sql);
    return resultSetHandler.handleCursorResultSets(statement);
  }

  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    //todo 直接通过JDBCConnection创建Statement对象
    if (mappedStatement.getResultSetType() == ResultSetType.DEFAULT) {
      return connection.createStatement();
    } else {
      //todo 设置结果集是可以滚动及其游标是否可以上下移动，设置结果集是否可更新
      return connection.createStatement(mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    }
  }

  //todo 因为直接调用statement来操作数据库，所以不能存在占位符，相应的 ,parameterize方法是空实现
  @Override
  public void parameterize(Statement statement) {
    // N/A
  }

}
