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

import java.util.Map;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 * todo 在动态SQL语句中构建IN条件语句的时候，通常需要对一个集合进行迭代，Mybatis提供了<foreach> 标签迭代集合时，不仅可以使用集合的元素和索引值
 *   还可以在循环开始之前或结束之后添加指定的字符串，也允许在迭代过程中添加指定的分隔符
 */
public class ForEachSqlNode implements SqlNode {
  public static final String ITEM_PREFIX = "__frch_";

  //todo 用于判断循环的终止条件，ForeachSqlNode构造方法中会创建对象
  private final ExpressionEvaluator evaluator;
  //todo 迭代的集合表达式
  private final String collectionExpression;
  //todo 记录了该ForeachSqlNode节点的子节点
  private final SqlNode contents;
  //todo 在循环开始前要添加的字符串
  private final String open;
  //todo 在循环结束后要添加的 字符串
  private final String close;
  //todo 循环过程中，每项之间的分隔符
  private final String separator;
  //todo index是当前迭代的次数，item的值是本次迭代的元素，若迭代集合是map,则index是键，item是值
  private final String item;
  private final String index;
  //todo 全局配置对象
  private final Configuration configuration;

  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }
  //todo 1）解析集合表达式，获取对应的实际参数
  // 2）在循环开始之前，添加open字段指定的字符串
  // 3）开始遍历集合，根据遍历的位置和是否指定分隔符
  // 4）调用applyIndex()方法将index添加到DynamicContext.bindings集合中，供后续解析使用
  // 5）调用applyItem()方法将item添加到DynamicContext.bingdings结合中，供后续使用
  // 6）转换子节点中的"#{}" 占位符，此步骤会将PrefixedContext封装成FilteredDynamicContext，在追加子节点转换结果时，就会使用FilterDynamicContext.apply()方法将
  //  "#{}" 占位符转换成 "#{__frch_}"的格式
  // 7）循环结束后，调用DynamicContext.appendSql 添加close指定的字符串
  @Override
  public boolean apply(DynamicContext context) {
    Map<String, Object> bindings = context.getBindings();
    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings);
    if (!iterable.iterator().hasNext()) {
      return true;
    }
    boolean first = true;
    applyOpen(context);
    int i = 0;
    for (Object o : iterable) {
      DynamicContext oldContext = context;
      if (first || separator == null) {
        context = new PrefixedContext(context, "");
      } else {
        context = new PrefixedContext(context, separator);
      }
      int uniqueNumber = context.getUniqueNumber();
      // Issue #709
      if (o instanceof Map.Entry) {
        @SuppressWarnings("unchecked")
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
        applyIndex(context, mapEntry.getKey(), uniqueNumber);
        applyItem(context, mapEntry.getValue(), uniqueNumber);
      } else {
        applyIndex(context, i, uniqueNumber);
        applyItem(context, o, uniqueNumber);
      }
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));
      if (first) {
        first = !((PrefixedContext) context).isPrefixApplied();
      }
      context = oldContext;
      i++;
    }
    applyClose(context);
    context.getBindings().remove(item);
    context.getBindings().remove(index);
    return true;
  }

  private void applyIndex(DynamicContext context, Object o, int i) {
    if (index != null) {
      context.bind(index, o);
      context.bind(itemizeItem(index, i), o);
    }
  }

  private void applyItem(DynamicContext context, Object o, int i) {
    if (item != null) {
      context.bind(item, o);
      context.bind(itemizeItem(item, i), o);
    }
  }

  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open);
    }
  }

  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }

  private static String itemizeItem(String item, int i) {
    return ITEM_PREFIX + item + "_" + i;
  }

  //todo 负责处理 #{} 占位符 ，但它并未完全解析 #{} 占位符
  private static class FilteredDynamicContext extends DynamicContext {
    //todo 底层封装的DynamicContext
    private final DynamicContext delegate;
    //todo 对应集合项在集合中的索引位置
    private final int index;
    //todo  对应集合项的index
    private final String itemIndex;
    //todo 对应集合项的item
    private final String item;

    public FilteredDynamicContext(Configuration configuration,DynamicContext delegate, String itemIndex, String item, int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
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
    public String getSql() {
      return delegate.getSql();
    }

    //todo 会将#{item}  占位符转换成"#{__frch_item_1}" 的格式， 其中"__frch_"是固定的前缀，"item" 与处理前的占位符一样，未发生改变 1则是Filter
    @Override
    public void appendSql(String sql) {
      //todo 创建GenericTokenParser 解析器，注意这里的匿名类是 TokenHandler对象
      GenericTokenParser parser = new GenericTokenParser("#{", "}", content -> {
        //todo 对item进行处理
        String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
        if (itemIndex != null && newContent.equals(content)) {
          //todo 对itemIndex进行处理
          newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
        }
        return "#{" + newContent + "}";
      });
      //todo 将解析的SQL语句片段 追击到delegate中保存
      delegate.appendSql(parser.parse(sql));
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }

  //todo 继承与DynamicContext，同时也都是DynamicContext的代理类
  private class PrefixedContext extends DynamicContext {
    //todo 底层封装的DynamicContext
    private final DynamicContext delegate;
    //todo 指定的前缀
    private final String prefix;
    //todo 是否已经处理过前缀
    private boolean prefixApplied;

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    //todo 会首先追加指定的prefix前缀到delegate,然后再将Sql语句片段追加到delegate中
    @Override
    public void appendSql(String sql) {
      //todo 判断是否需要追加前缀
      if (!prefixApplied && sql != null && sql.trim().length() > 0) {
        //todo 追加前缀
        delegate.appendSql(prefix);
        //todo 表示已经处理前缀
        prefixApplied = true;
      }
      //todo 追加sql片段
      delegate.appendSql(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
