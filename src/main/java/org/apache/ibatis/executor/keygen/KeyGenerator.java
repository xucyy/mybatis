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
package org.apache.ibatis.executor.keygen;

import java.sql.Statement;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;

/**
 * @author Clinton Begin
 * todo  可以通过KeyGenerator 的功能使insert语句返回插入语句的主键
 *
 *   Generator=生成器  KeyGenerator=主键生成器
 */
public interface KeyGenerator {
  //todo 在执行insert之前执行，设置属性order="BEFORE"
  void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter);
  //todo 在执行insert 之后执行，设置属性order="AFTER"
  void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

}
