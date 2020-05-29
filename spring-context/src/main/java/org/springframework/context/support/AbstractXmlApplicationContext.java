/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.context.support;

import java.io.IOException;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.ResourceEntityResolver;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;

/**
 * Convenient base class for
 * {@link org.springframework.context.ApplicationContext} implementations,
 * drawing configuration from XML documents containing bean definitions
 * understood by an
 * {@link org.springframework.beans.factory.xml.XmlBeanDefinitionReader}.
 *
 * <p>
 * Subclasses just have to implement the {@link #getConfigResources} and/or the
 * {@link #getConfigLocations} method. Furthermore, they might override the
 * {@link #getResourceByPath} hook to interpret relative paths in an
 * environment-specific fashion, and/or {@link #getResourcePatternResolver} for
 * extended pattern resolution.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #getConfigResources
 * @see #getConfigLocations
 * @see org.springframework.beans.factory.xml.XmlBeanDefinitionReader
 */
public abstract class AbstractXmlApplicationContext extends AbstractRefreshableConfigApplicationContext {

	private boolean validating = true;

	/**
	 * Create a new AbstractXmlApplicationContext with no parent.
	 */
	public AbstractXmlApplicationContext() {
	}

	/**
	 * Create a new AbstractXmlApplicationContext with the given parent context.
	 * 
	 * @param parent
	 *            the parent context
	 */
	public AbstractXmlApplicationContext(@Nullable ApplicationContext parent) {
		super(parent);
	}

	/**
	 * Set whether to use XML validation. Default is {@code true}.
	 */
	public void setValidating(boolean validating) {
		this.validating = validating;
	}

	/**
	 * Loads the bean definitions via an XmlBeanDefinitionReader.
	 * 
	 * @see org.springframework.beans.factory.xml.XmlBeanDefinitionReader
	 * @see #initBeanDefinitionReader
	 * @see #loadBeanDefinitions
	 * todo 加载 BeanDefinition
	 */
	@Override
	protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws BeansException, IOException {
		// Create a new XmlBeanDefinitionReader for the given BeanFactory.
		//todo 创建一个BeanDefinition阅读器，通过阅读XML文件，真正完成BeanDefinition的加载和注册
		//todo 为指定 beanFactory 创建 XmlBeanDefinitionReader
		XmlBeanDefinitionReader beanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);

		// Configure the bean definition reader with this context's
		// resource loading environment.
		//todo 対beanDefinitionReader进行环境变量的设置
		beanDefinitionReader.setEnvironment(this.getEnvironment());
		beanDefinitionReader.setResourceLoader(this);
		beanDefinitionReader.setEntityResolver(new ResourceEntityResolver(this));

		// Allow a subclass to provide custom initialization of the reader,
		// then proceed with actually loading the bean definitions.
		//todo 对 BeanDefinitionReader 进行设置.可以覆盖
		initBeanDefinitionReader(beanDefinitionReader);
	
		//todo 委托给BeanDefinition阅读器去加载BeanDefinition
		loadBeanDefinitions(beanDefinitionReader);
	}

	/**
	 * Initialize the bean definition reader used for loading the bean definitions
	 * of this context. Default implementation is empty.
	 * <p>
	 * Can be overridden in subclasses, e.g. for turning off XML validation or using
	 * a different XmlBeanDefinitionParser implementation.
	 * 
	 * @param reader
	 *            the bean definition reader used by this context
	 * @see org.springframework.beans.factory.xml.XmlBeanDefinitionReader#setDocumentReaderClass
	 */
	protected void initBeanDefinitionReader(XmlBeanDefinitionReader reader) {
		reader.setValidating(this.validating);
	}

	/**
	 * Load the bean definitions with the given XmlBeanDefinitionReader.
	 * <p>
	 * The lifecycle of the bean factory is handled by the
	 * {@link #refreshBeanFactory} method; hence this method is just supposed to
	 * load and/or register bean definitions.
	 * 
	 * @param reader
	 *            the XmlBeanDefinitionReader to use
	 * @throws BeansException
	 *             in case of bean registration errors
	 * @throws IOException
	 *             if the required XML document isn't found
	 * @see #refreshBeanFactory
	 * @see #getConfigLocations
	 * @see #getResources
	 * @see #getResourcePatternResolver
	 * todo XmlBeanDefinitionReader中已经将之前初始化的 DefaultListableBeanFactory 注册进去了，
	 * todo 所以 XmlBeanDefinitionReader 所读 取的BeanDefinitionHolder都会注册到DefaultListableBeanFactory中
	 * todo ，也就是经过此步骤，类型 DefaultListableBeanFactory的变量beanFactory已经包含所有解析好的配置
	 *
	 */
	protected void loadBeanDefinitions(XmlBeanDefinitionReader reader) throws BeansException, IOException {
		//todo 获取资源的定位
		//todo 这里getConfigResources是一个空实现，真正实现是调用子类的获取资源定位的方法
		//todo 比如：ClassPathXmlApplicationContext中进行了实现
		//todo 		而FileSystemXmlApplicationContext没有使用该方法
		Resource[] configResources = getConfigResources();
		if (configResources != null) {
			//todo XML Bean读取器调用其父类AbstractBeanDefinitionReader读取定位的资源
			reader.loadBeanDefinitions(configResources);
		}
		//todo 如果子类中获取的资源定位为空，则获取FileSystemXmlApplicationContext构造方法中setConfigLocations方法设置的资源
		String[] configLocations = getConfigLocations();
		if (configLocations != null) {
			//todo XML Bean读取器调用其父类AbstractBeanDefinitionReader读取定位的资源
			reader.loadBeanDefinitions(configLocations);
		}
	}

	/**
	 * Return an array of Resource objects, referring to the XML bean definition
	 * files that this context should be built with.
	 * <p>
	 * The default implementation returns {@code null}. Subclasses can override this
	 * to provide pre-built Resource objects rather than location Strings.
	 * 
	 * @return an array of Resource objects, or {@code null} if none
	 * @see #getConfigLocations()
	 */
	@Nullable
	protected Resource[] getConfigResources() {
		return null;
	}

}
