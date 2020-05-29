/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of the {@link BeanDefinitionDocumentReader} interface that
 * reads bean definitions according to the "spring-beans" DTD and XSD format
 * (Spring's default XML bean definition format).
 *
 * <p>The structure, elements, and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). {@code <beans>} does not need to be the root
 * element of the XML document: this class will parse all bean definition elements
 * in the XML file, regardless of the actual root element.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Erik Wiersma
 * @since 18.12.2003
 * TODO 发现这个方法的重要目的之一就是提取root,以便于再次将root作为参数继续 BeanDefinition 的注册
 */
public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {

	public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;

	public static final String NESTED_BEANS_ELEMENT = "beans";

	public static final String ALIAS_ELEMENT = "alias";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String ALIAS_ATTRIBUTE = "alias";

	public static final String IMPORT_ELEMENT = "import";

	public static final String RESOURCE_ATTRIBUTE = "resource";

	public static final String PROFILE_ATTRIBUTE = "profile";


	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private XmlReaderContext readerContext;

	@Nullable
	private BeanDefinitionParserDelegate delegate;


	/**
	 * This implementation parses bean definitions according to the "spring-beans" XSD
	 * (or DTD, historically).
	 * <p>Opens a DOM Document; then initializes the default settings
	 * specified at the {@code <beans/>} level; then parses the contained bean definitions.
	 */
	@Override
	public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
		this.readerContext = readerContext;
		logger.debug("Loading bean definitions");
		//TODO 获得Document的根元素<beans>标签
		Element root = doc.getDocumentElement();
		//TODO 真正实现BeanDefinition解析和注册工作
		doRegisterBeanDefinitions(root);
	}

	/**
	 * Return the descriptor for the XML resource that this parser works on.
	 */
	protected final XmlReaderContext getReaderContext() {
		Assert.state(this.readerContext != null, "No XmlReaderContext available");
		return this.readerContext;
	}

	/**
	 * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor}
	 * to pull the source metadata from the supplied {@link Element}.
	 */
	@Nullable
	protected Object extractSource(Element ele) {
		return getReaderContext().extractSource(ele);
	}


	/**
	 * Register each bean definition within the given root {@code <beans/>} element.
	 */
	protected void doRegisterBeanDefinitions(Element root) {
		// Any nested <beans> elements will cause recursion in this method. In
		// order to propagate and preserve <beans> default-* attributes correctly,
		// keep track of the current (parent) delegate, which may be null. Create
		// the new (child) delegate with a reference to the parent for fallback purposes,
		// then ultimately reset this.delegate back to its original (parent) reference.
		// this behavior emulates a stack of delegates without actually necessitating one.
		
		//TODO  这里使用了委托模式，将具体的BeanDefinition解析工作交给了BeanDefinitionParserDelegate去完成
		BeanDefinitionParserDelegate parent = this.delegate;
		this.delegate = createDelegate(getReaderContext(), root, parent);

		//todo 判断该根标签是否包含http://www.springframework.org/schema/beans默认命名空间
		if (this.delegate.isDefaultNamespace(root)) {
			//TODO 处理profile 属性
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
			if (StringUtils.hasText(profileSpec)) {
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					if (logger.isInfoEnabled()) {
						logger.info("Skipped XML bean definition file due to specified profiles [" + profileSpec +
								"] not matching: " + getReaderContext().getResource());
					}
					return;
				}
			}
		}
		//TODO  解析前处理.留给了类实现 在解析Bean定义之前，进行自定义的解析，增强解析过程的可扩展性
		preProcessXml(root);
		//TODO 委托给BeanDefinitionParserDelegate,从Document的根元素开始进行BeanDefinition的解析
		parseBeanDefinitions(root, this.delegate);
		//TODO 解析后处理.留给子类实现  在解析Bean定义之后，进行自定义的解析，增加解析过程的可扩展性
		postProcessXml(root);

		this.delegate = parent;
	}

	protected BeanDefinitionParserDelegate createDelegate(
			XmlReaderContext readerContext, Element root, @Nullable BeanDefinitionParserDelegate parentDelegate) {

		BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
		delegate.initDefaults(root, parentDelegate);
		return delegate;
	}

	/**
	 * Parse the elements at the root level in the document:
	 * "import", "alias", "bean".
	 * @param root the DOM root element of the document
	 */
	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		//TODO 对beans 加载的Document对象是否使用了Spring默认的XML命名空间（beans命名空间）
		if (delegate.isDefaultNamespace(root)) {
			//TODO 获取Document对象根元素的所有子节点（bean标签、import标签、alias标签和其他自定义标签context、aop等）
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (node instanceof Element) {
					Element ele = (Element) node;
					//TODO bean标签、import标签、alias标签，则使用默认解析规则
					if (delegate.isDefaultNamespace(ele)) {
						//todo 默认标签解析
						parseDefaultElement(ele, delegate);
					}
					else {//TODO 像context标签、aop标签、tx标签，则使用用户自定义的解析规则解析元素节点
						//todo 自定义标签的解析
						delegate.parseCustomElement(ele);
					}
				}
			}
		}
		else {
			//TODO 如果不是默认的命名空间，则使用用户自定义的解析规则解析元素节点
			//todo 自定义标签的解析
			delegate.parseCustomElement(root);
		}
	}

	/**
	 * todo 默认解析
	 * @param ele
	 * @param delegate
	 */
	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		//TODO 解析<import>标签
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			importBeanDefinitionResource(ele);
		}
		//TODO 解析<alias>标签
		else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			processAliasRegistration(ele);
		}
		//TODO 解析<bean>标签
		else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			processBeanDefinition(ele, delegate);
		}
		//TODO 解析内置<beans>标签
		else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			//TODO recurse
			//TODO 递归调用
			doRegisterBeanDefinitions(ele);
		}
	}

	/**
	 * Parse an "import" element and load the bean definitions
	 * from the given resource into the bean factory.
	 *
	 * 对于Spring配置文件的编写，我想，经历过庞大项目的人，都有那种恐惧的心理，太多的 配置文件了。不过，
	 * 分模块是大多数人能想到的方法，但是，怎么分模块，那就仁者见仁，智 者见智了。使用import是个好办法，
	 * 例如我们可以构造这样的Spring配置文件：
	 applicationContext.xml
	 <?xml version35*! . 0n encoding=Mgb2312w?>
	 <!DOCTYPE beans PUBLIC W-//Spring//DTD BEAN//EN” nhttp://www.Springframework.org/ dtd/Spring-beans.dtd">
	 <beans>
	 <import resource="customerContext.xmlw />
	 <import resource»HsystemContext.xml*1 />
	 </beans>
	 */
	protected void importBeanDefinitionResource(Element ele) {
		//TODO 获取resource属性  (1)获取resource属性所表示的路径
		String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
		//TODO 如果不存在resource属性则不做任何处理
		if (!StringUtils.hasText(location)) {
			getReaderContext().error("Resource location must not be empty", ele);
			return;
		}

		// Resolve system properties: e.g. "${user.dir}"
		//todo 解析系统属性.格式如："${user.dir}"  (2)解析路径中的系统属性，格式如“${useRdir}”
		location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

		Set<Resource> actualResources = new LinkedHashSet<>(4);

		// Discover whether the location is an absolute or relative URI
		//TODO 判定location 是决定URI还是URL  (3 )判定location是绝对路径还是相对路径。
		boolean absoluteLocation = false;
		try {
			absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
		}
		catch (URISyntaxException ex) {
			// cannot convert to an URI, considering the location relative
			// unless it is the well-known Spring prefix "classpath*:"
		}

		// Absolute or relative?
		//todo 如果是绝对URI则直接根据地址加载对应的配置文件   (4)如果是绝对路径则递归调用bean的解析过程，进行另一次的解析
		if (absoluteLocation) {
			try {
				//TODO 递归调用beans的解析过程
				int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
				if (logger.isDebugEnabled()) {
					logger.debug("Imported " + importCount + " bean definitions from URL location [" + location + "]");
				}
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from URL location [" + location + "]", ele, ex);
			}
		}
		else {
			// No URL -> considering resource location as relative to the current file.
			try {
				//TODO 如果是相对地址则根据相对地址计算出绝対地址 (5)如果是相对路径则计算出绝对路径并进行解析。
				int importCount;
				//TODO Resource 存在多个子实现类.如 vfsResource, FileSystemResource 等.
				//TODO 而每个resource的createRelative方式实现都不一样，所以这里先使用子类的方法 尝试解析
				Resource relativeResource = getReaderContext().getResource().createRelative(location);
				if (relativeResource.exists()) {
					importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
					actualResources.add(relativeResource);
				}
				else {
					//TODO 如果解析不成功，则使用默认的解析器ResourcePatternResolver进行解析
					String baseLocation = getReaderContext().getResource().getURL().toString();
					//TODO  递归调用beans的解析过程
					importCount = getReaderContext().getReader().loadBeanDefinitions(
							StringUtils.applyRelativePath(baseLocation, location), actualResources);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Imported " + importCount + " bean definitions from relative location [" + location + "]");
				}
			}
			catch (IOException ex) {
				getReaderContext().error("Failed to resolve current resource location", ele, ex);
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to import bean definitions from relative location [" + location + "]",
						ele, ex);
			}
		}
		//TODO 解析后进行监听器激活处理 (6)通知监听器，解析完成。
		Resource[] actResArray = actualResources.toArray(new Resource[0]);
		getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
	}

	/**
	 * Process the given alias element, registering the alias with the registry.
	 */
	protected void processAliasRegistration(Element ele) {
		//TODO 获取 BeanName
		String name = ele.getAttribute(NAME_ATTRIBUTE);
		//TODO 获取 alias
		String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
		boolean valid = true;
		if (!StringUtils.hasText(name)) {
			getReaderContext().error("Name must not be empty", ele);
			valid = false;
		}
		if (!StringUtils.hasText(alias)) {
			getReaderContext().error("Alias must not be empty", ele);
			valid = false;
		}
		if (valid) {
			try {
				//TODO 注册别名
				getReaderContext().getRegistry().registerAlias(name, alias);
			}
			catch (Exception ex) {
				getReaderContext().error("Failed to register alias '" + alias +
						"' for bean with name '" + name + "'", ele, ex);
			}
			//TODO 别名注册后通知监听器做相应处理
			getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
		}
	}

	/**
	 * Process the given bean element, parsing the bean definition
	 * and registering it with the registry.
	 */
	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		//TODO 解析<bean>标签，获取BeanDefinition
		//TODO （1 ）首先委托 BeanDefinitionDelegate 类的 parseBeanDefinitionElement 方法进行元素解析,
		//TODO 返冋BeanDefinitionHolder类型的实例bdHolder,
		//TODO 经过这个方法后，bdHolder实例已经包含我 们配置文件中配置的各种属性了，例如class、name、id、alias之类的属性  解析完了
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			//TODO 当返回的bdHolder不为空的情况下若存在默认标签的子节点下再有自定义届性，还需 要再次对自定义标签进行解析
			//TODO 如果需要，则装饰BeanDefinition对象 装饰完了
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				//TODO 解析完成后，需要对解析后的bdHolder进行注册，同样，注册操作委托给了
				//TODO BeanDefinitionReaderUtils的registerBeanDefinition 方法 注册最终的BeanDefinition
				//ToDo 到BeanDefinitionRegistry
				//TODO （DefaultListableBeanFactory） 注册
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			//TODo 最后发岀响应事件，通知相关的监听器，这个bean已经加载完成了
			//TODO 这里的实现只为扩展，当程序开发人员需要对注册BeanDefinition事件进行监听时可以通过注册监听器的方式并将处理逻辑写入监听器中，
			//todo 目前在Spring中并没有对此事件做 任何逻辑处理
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}


	/**
	 * Allow the XML to be extensible by processing any custom element types first,
	 * before we start to process the bean definitions. This method is a natural
	 * extension point for any other custom pre-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void preProcessXml(Element root) {
	}

	/**
	 * Allow the XML to be extensible by processing any custom element types last,
	 * after we finished processing the bean definitions. This method is a natural
	 * extension point for any other custom post-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void postProcessXml(Element root) {
	}

}
