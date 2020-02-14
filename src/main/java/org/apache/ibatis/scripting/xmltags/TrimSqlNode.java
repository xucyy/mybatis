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
package org.apache.ibatis.scripting.xmltags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 * todo 会根据子节点的解析结果，添加或删除相应的前缀或后缀
 *   它的流程就是 ，我会设置一些不能开头和结尾处有的一些符号，比如where 逗号，然后 就先解析子节点，等子节点都解析完了，就看解析后的sql语句，前缀和后缀是不是有我
 *    设置的这些不能在前缀或者后缀出现的符号，如果最后有个逗号，那肯定不对，这时候需要处理逗号，将逗号删除
 */
public class TrimSqlNode implements SqlNode {

  //todo 该<trim>节点的子节点
  private final SqlNode contents;
  //todo 记录了前缀字符串(为<trim>节点包裹的SQL语句添加的前缀)
  private final String prefix;
  //todo 记录了后缀字符串（为<trim>节点包裹的SQL语句添加的后缀）
  private final String suffix;
  //todo 如果<trim>节点包裹的SQL语句是空语句（经常出现在if判断为否的情况下），删除指定的前缀 ，比如where
  private final List<String> prefixesToOverride;
  //todo 如果<trim>节点包裹的SQL语句是空语句（经常出现在if判断为否的情况下），删除指定的后缀，比如逗号
  private final List<String> suffixesToOverride;
  private final Configuration configuration;

  public TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, String prefixesToOverride, String suffix, String suffixesToOverride) {
    this(configuration, contents, prefix, parseOverrides(prefixesToOverride), suffix, parseOverrides(suffixesToOverride));
  }

  protected TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, List<String> prefixesToOverride, String suffix, List<String> suffixesToOverride) {
    this.contents = contents;
    this.prefix = prefix;
    this.prefixesToOverride = prefixesToOverride;
    this.suffix = suffix;
    this.suffixesToOverride = suffixesToOverride;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
    //todo 创建FilteredDynamicContext 其中封装了 DynamicContext
    FilteredDynamicContext filteredDynamicContext = new FilteredDynamicContext(context);
    //todo 调用子类的apply()方法进行解析
    boolean result = contents.apply(filteredDynamicContext);
    //todo 使用filteredDynamicContext.applyAll方法处理前缀和后缀
    filteredDynamicContext.applyAll();
    return result;
  }
  //todo 对参数prefixesToOverride（对应<trim>节点的prefixOverrides属性） 和参数suffixesToOverride（对应<trim>节点的suffixOverrides属性）进行解析，根据结果初始化
  //  prefixesToOverride和suffixesToOverride
  private static List<String> parseOverrides(String overrides) {
    if (overrides != null) {
      //todo 按照"|" 进行分割
      final StringTokenizer parser = new StringTokenizer(overrides, "|", false);
      final List<String> list = new ArrayList<>(parser.countTokens());
      //todo 转换为大写，并添加到集合中
      while (parser.hasMoreTokens()) {
        list.add(parser.nextToken().toUpperCase(Locale.ENGLISH));
      }
      return list;
    }
    return Collections.emptyList();
  }
  //todo 处理前缀和后缀
  private class FilteredDynamicContext extends DynamicContext {
    //todo 底层封装的DynamicContext对象
    private DynamicContext delegate;
    //todo 是否已经处理过前缀和后缀，默认是false
    private boolean prefixApplied;
    private boolean suffixApplied;
    //todo  用于记录子节点解析后的结果，appendSql 是向该 StringBuilder中添加结果
    private StringBuilder sqlBuffer;

    public FilteredDynamicContext(DynamicContext delegate) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefixApplied = false;
      this.suffixApplied = false;
      this.sqlBuffer = new StringBuilder();
    }

    public void applyAll() {
      //todo 获取子节点解析后的结果，全部转换为大写
      sqlBuffer = new StringBuilder(sqlBuffer.toString().trim());
      String trimmedUppercaseSql = sqlBuffer.toString().toUpperCase(Locale.ENGLISH);
      if (trimmedUppercaseSql.length() > 0) {
        //todo 处理前缀
        applyPrefix(sqlBuffer, trimmedUppercaseSql);
        //todo 处理后缀
        applySuffix(sqlBuffer, trimmedUppercaseSql);
      }
      //todo 将解析后的结果添加到delegate中
      delegate.appendSql(sqlBuffer.toString());
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

    @Override
    public void appendSql(String sql) {
      sqlBuffer.append(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    //todo 处理前缀方法
    private void applyPrefix(StringBuilder sql, String trimmedUppercaseSql) {
      //todo 检测是否已经处理过前缀
      if (!prefixApplied) {
        //todo 标记已处理过前缀
        prefixApplied = true;
        if (prefixesToOverride != null) {
          //todo 遍历prefixesToOverride 集合
          for (String toRemove : prefixesToOverride) {
            //todo 如果 以prefixesToOverride 的某项开头，则将该项从SQL语句开头删除掉
            if (trimmedUppercaseSql.startsWith(toRemove)) {
              sql.delete(0, toRemove.trim().length());
              break;
            }
          }
        }
        if (prefix != null) {
          //todo 添加prefix前缀
          sql.insert(0, " ");
          sql.insert(0, prefix);
        }
      }
    }
    //todo 处理后缀方法
    private void applySuffix(StringBuilder sql, String trimmedUppercaseSql) {
      //todo 检测是否已经处理过后缀
      if (!suffixApplied) {
        //todo 标记 已处理
        suffixApplied = true;
        if (suffixesToOverride != null) {
          //todo 遍历suffixesToOverride集合
          for (String toRemove : suffixesToOverride) {
            //todo 如果以suffixesToOverride中某项结尾，则将该项从SQl语句结尾删除掉
            if (trimmedUppercaseSql.endsWith(toRemove) || trimmedUppercaseSql.endsWith(toRemove.trim())) {
              int start = sql.length() - toRemove.trim().length();
              int end = sql.length();
              sql.delete(start, end);
              break;
            }
          }
        }
        if (suffix != null) {
          //todo 添加suffix后缀
          sql.append(" ");
          sql.append(suffix);
        }
      }
    }

  }

}
