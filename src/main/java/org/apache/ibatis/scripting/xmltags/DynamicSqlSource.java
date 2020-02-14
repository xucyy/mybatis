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
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 * todo 动态SQL,负责处理动态SQL语句
 *   DynamicSqlSource中封装的Sql语句还需要进行一些列的解析，才会最终形成数据库可执行的SQL语句
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  //todo SQLnode中使用了组合模式，形成一个树装结构，使用rootSqlNode记录了待极细SQLNode树的根节点
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    //todo 创建 DynamicContext对象，parameterObject是用户传入的实参
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    //todo 通过调用 apply方法调用整个树形结构中全部SqlNode.apply()方法，每个SqlNode的apply()方法都将解析得到的SQL语句追加
    //  到context中，最终通过context.getSql得到完整的SQL语句
    rootSqlNode.apply(context);
    //todo 创建SqlSourceBuilder
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    //todo  创建 SqlSource
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    //todo  创建BoundSql对象，并将DynamicContext.bindings中的参数复制到其additionalParameters集合中保存
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql;
  }

}
