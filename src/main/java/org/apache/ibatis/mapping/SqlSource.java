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
package org.apache.ibatis.mapping;

/**
 * Represents the content of a mapped statement read from an XML file or an annotation.
 * It creates the SQL that will be passed to the database out of the input parameter received from the user.
 * todo 表示映射文件或注解中定义的SQL语句，但是它表示的SQL语句是不能直接被数据库执行的，因为其中可能含有动态SQL语句相关的
 *   节点或是占位符等需要解析的元素
 * @author Clinton Begin
 */
public interface SqlSource {
  //todo getBoundSql()方法会根据映射文件或注解描述的SQL语句，以及传入的参数，返回可执行的SQL
  //  不管是StaticSqlSource,DynamicSqlSource,RawSqlSource ，最终都会统一生成BoundSql对象，其中封装了完整的SQL语句
  //  （可能包含"？"占位符），参数映射关系(parameterMappings集合)以及用户传入的参数(additionalParameters集合)。
  //  另外DynamicSqlSource负责处理动态SQL语句，RawSqlSource负责处理静态SQL语句，除此之外，两者解析SQL语句的时机也不一样
  //  前者解析的时机是在实际执行SQL语句之前，而后者则是在mybatis初始化时完成SQL语句的解析
  BoundSql getBoundSql(Object parameterObject);

}
