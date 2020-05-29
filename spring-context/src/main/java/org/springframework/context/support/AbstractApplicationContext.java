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

package org.springframework.context.support;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.support.ResourceEditorRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.context.weaving.LoadTimeWeaverAware;
import org.springframework.context.weaving.LoadTimeWeaverAwareProcessor;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Abstract implementation of the {@link org.springframework.context.ApplicationContext}
 * interface. Doesn't mandate the type of storage used for configuration; simply
 * implements common context functionality. Uses the Template Method design pattern,
 * requiring concrete subclasses to implement abstract methods.
 *
 * <p>In contrast to a plain BeanFactory, an ApplicationContext is supposed
 * to detect special beans defined in its internal bean factory:
 * Therefore, this class automatically registers
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor BeanFactoryPostProcessors},
 * {@link org.springframework.beans.factory.config.BeanPostProcessor BeanPostProcessors}
 * and {@link org.springframework.context.ApplicationListener ApplicationListeners}
 * which are defined as beans in the context.
 *
 * <p>A {@link org.springframework.context.MessageSource} may also be supplied
 * as a bean in the context, with the name "messageSource"; otherwise, message
 * resolution is delegated to the parent context. Furthermore, a multicaster
 * for application events can be supplied as "applicationEventMulticaster" bean
 * of type {@link org.springframework.context.event.ApplicationEventMulticaster}
 * in the context; otherwise, a default multicaster of type
 * {@link org.springframework.context.event.SimpleApplicationEventMulticaster} will be used.
 *
 * <p>Implements resource loading through extending
 * {@link org.springframework.core.io.DefaultResourceLoader}.
 * Consequently treats non-URL resource paths as class path resources
 * (supporting full class path resource names that include the package path,
 * e.g. "mypackage/myresource.dat"), unless the {@link #getResourceByPath}
 * method is overwritten in a subclass.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @since January 21, 2001
 * @see #refreshBeanFactory
 * @see #getBeanFactory
 * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.context.event.ApplicationEventMulticaster
 * @see org.springframework.context.ApplicationListener
 * @see org.springframework.context.MessageSource
 */
public abstract class AbstractApplicationContext extends DefaultResourceLoader
		implements ConfigurableApplicationContext {

	/**
	 * Name of the MessageSource bean in the factory.
	 * If none is supplied, message resolution is delegated to the parent.
	 * @see MessageSource
	 */
	public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";

	/**
	 * Name of the LifecycleProcessor bean in the factory.
	 * If none is supplied, a DefaultLifecycleProcessor is used.
	 * @see org.springframework.context.LifecycleProcessor
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 */
	public static final String LIFECYCLE_PROCESSOR_BEAN_NAME = "lifecycleProcessor";

	/**
	 * Name of the ApplicationEventMulticaster bean in the factory.
	 * If none is supplied, a default SimpleApplicationEventMulticaster is used.
	 * @see org.springframework.context.event.ApplicationEventMulticaster
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 */
	public static final String APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster";


	static {
		// Eagerly load the ContextClosedEvent class to avoid weird classloader issues
		// on application shutdown in WebLogic 8.1. (Reported by Dustin Woods.)
		ContextClosedEvent.class.getName();
	}


	/** Logger used by this class. Available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	/** Unique id for this context, if any */
	private String id = ObjectUtils.identityToString(this);

	/** Display name */
	private String displayName = ObjectUtils.identityToString(this);

	/** Parent context */
	@Nullable
	private ApplicationContext parent;

	/** Environment used by this context */
	@Nullable
	private ConfigurableEnvironment environment;

	/** BeanFactoryPostProcessors to apply on refresh */
	private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = new ArrayList<>();

	/** System time in milliseconds when this context started */
	private long startupDate;

	/** Flag that indicates whether this context is currently active */
	private final AtomicBoolean active = new AtomicBoolean();

	/** Flag that indicates whether this context has been closed already */
	private final AtomicBoolean closed = new AtomicBoolean();

	/** Synchronization monitor for the "refresh" and "destroy" */
	private final Object startupShutdownMonitor = new Object();

	/** Reference to the JVM shutdown hook, if registered */
	@Nullable
	private Thread shutdownHook;

	/** ResourcePatternResolver used by this context */
	private ResourcePatternResolver resourcePatternResolver;

	/** LifecycleProcessor for managing the lifecycle of beans within this context */
	@Nullable
	private LifecycleProcessor lifecycleProcessor;

	/** MessageSource we delegate our implementation of this interface to */
	@Nullable
	private MessageSource messageSource;

	/** Helper class used in event publishing */
	@Nullable
	private ApplicationEventMulticaster applicationEventMulticaster;

	/** Statically specified listeners */
	private final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();

	/** ApplicationEvents published early */
	@Nullable
	private Set<ApplicationEvent> earlyApplicationEvents;


	/**
	 * Create a new AbstractApplicationContext with no parent.
	 */
	public AbstractApplicationContext() {
		this.resourcePatternResolver = getResourcePatternResolver();
	}

	/**
	 * Create a new AbstractApplicationContext with the given parent context.
	 * @param parent the parent context
	 */
	public AbstractApplicationContext(@Nullable ApplicationContext parent) {
		this();
		setParent(parent);
	}


	//---------------------------------------------------------------------
	// Implementation of ApplicationContext interface
	//---------------------------------------------------------------------

	/**
	 * Set the unique id of this application context.
	 * <p>Default is the object id of the context instance, or the name
	 * of the context bean if the context is itself defined as a bean.
	 * @param id the unique id of the context
	 */
	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public String getApplicationName() {
		return "";
	}

	/**
	 * Set a friendly name for this context.
	 * Typically done during initialization of concrete context implementations.
	 * <p>Default is the object id of the context instance.
	 */
	public void setDisplayName(String displayName) {
		Assert.hasLength(displayName, "Display name must not be empty");
		this.displayName = displayName;
	}

	/**
	 * Return a friendly name for this context.
	 * @return a display name for this context (never {@code null})
	 */
	@Override
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * Return the parent context, or {@code null} if there is no parent
	 * (that is, this context is the root of the context hierarchy).
	 */
	@Override
	@Nullable
	public ApplicationContext getParent() {
		return this.parent;
	}

	/**
	 * Set the {@code Environment} for this application context.
	 * <p>Default value is determined by {@link #createEnvironment()}. Replacing the
	 * default with this method is one option but configuration through {@link
	 * #getEnvironment()} should also be considered. In either case, such modifications
	 * should be performed <em>before</em> {@link #refresh()}.
	 * @see org.springframework.context.support.AbstractApplicationContext#createEnvironment
	 */
	@Override
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * Return the {@code Environment} for this application context in configurable
	 * form, allowing for further customization.
	 * <p>If none specified, a default environment will be initialized via
	 * {@link #createEnvironment()}.
	 */
	@Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			this.environment = createEnvironment();
		}
		return this.environment;
	}

	/**
	 * Create and return a new {@link StandardEnvironment}.
	 * <p>Subclasses may override this method in order to supply
	 * a custom {@link ConfigurableEnvironment} implementation.
	 */
	protected ConfigurableEnvironment createEnvironment() {
		return new StandardEnvironment();
	}

	/**
	 * Return this context's internal bean factory as AutowireCapableBeanFactory,
	 * if already available.
	 * @see #getBeanFactory()
	 */
	@Override
	public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
		return getBeanFactory();
	}

	/**
	 * Return the timestamp (ms) when this context was first loaded.
	 */
	@Override
	public long getStartupDate() {
		return this.startupDate;
	}

	/**
	 * Publish the given event to all listeners.
	 * <p>Note: Listeners get initialized after the MessageSource, to be able
	 * to access it within listener implementations. Thus, MessageSource
	 * implementations cannot publish events.
	 * @param event the event to publish (may be application-specific or a
	 * standard framework event)
	 */
	@Override
	public void publishEvent(ApplicationEvent event) {
		publishEvent(event, null);
	}

	/**
	 * Publish the given event to all listeners.
	 * <p>Note: Listeners get initialized after the MessageSource, to be able
	 * to access it within listener implementations. Thus, MessageSource
	 * implementations cannot publish events.
	 * @param event the event to publish (may be an {@link ApplicationEvent}
	 * or a payload object to be turned into a {@link PayloadApplicationEvent})
	 */
	@Override
	public void publishEvent(Object event) {
		publishEvent(event, null);
	}

	/**
	 * Publish the given event to all listeners.
	 * @param event the event to publish (may be an {@link ApplicationEvent}
	 * or a payload object to be turned into a {@link PayloadApplicationEvent})
	 * @param eventType the resolved event type, if known
	 * @since 4.2
	 * todo  当完成Applicationcontext初始化的时候，要通过Spring中的事件发布机制来发出ContextRefreshedEvent事件，
	   todo 以保证对应的监听器可以做进一步的逻辑处理
	 */
	protected void publishEvent(Object event, @Nullable ResolvableType eventType) {
		Assert.notNull(event, "Event must not be null");
		if (logger.isTraceEnabled()) {
			logger.trace("Publishing event in " + getDisplayName() + ": " + event);
		}

		// Decorate event as an ApplicationEvent if necessary
		ApplicationEvent applicationEvent;
		if (event instanceof ApplicationEvent) {
			applicationEvent = (ApplicationEvent) event;
		}
		else {
			applicationEvent = new PayloadApplicationEvent<>(this, event);
			if (eventType == null) {
				eventType = ((PayloadApplicationEvent) applicationEvent).getResolvableType();
			}
		}

		// Multicast right now if possible - or lazily once the multicaster is initialized
		if (this.earlyApplicationEvents != null) {
			this.earlyApplicationEvents.add(applicationEvent);
		}
		else {
			getApplicationEventMulticaster().multicastEvent(applicationEvent, eventType);
		}

		// Publish event via parent context as well...
		if (this.parent != null) {
			if (this.parent instanceof AbstractApplicationContext) {
				((AbstractApplicationContext) this.parent).publishEvent(event, eventType);
			}
			else {
				this.parent.publishEvent(event);
			}
		}
	}

	/**
	 * Return the internal ApplicationEventMulticaster used by the context.
	 * @return the internal ApplicationEventMulticaster (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	ApplicationEventMulticaster getApplicationEventMulticaster() throws IllegalStateException {
		if (this.applicationEventMulticaster == null) {
			throw new IllegalStateException("ApplicationEventMulticaster not initialized - " +
					"call 'refresh' before multicasting events via the context: " + this);
		}
		return this.applicationEventMulticaster;
	}

	/**
	 * Return the internal LifecycleProcessor used by the context.
	 * @return the internal LifecycleProcessor (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	LifecycleProcessor getLifecycleProcessor() throws IllegalStateException {
		if (this.lifecycleProcessor == null) {
			throw new IllegalStateException("LifecycleProcessor not initialized - " +
					"call 'refresh' before invoking lifecycle methods via the context: " + this);
		}
		return this.lifecycleProcessor;
	}

	/**
	 * Return the ResourcePatternResolver to use for resolving location patterns
	 * into Resource instances. Default is a
	 * {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver},
	 * supporting Ant-style location patterns.
	 * <p>Can be overridden in subclasses, for extended resolution strategies,
	 * for example in a web environment.
	 * <p><b>Do not call this when needing to resolve a location pattern.</b>
	 * Call the context's {@code getResources} method instead, which
	 * will delegate to the ResourcePatternResolver.
	 * @return the ResourcePatternResolver for this context
	 * @see #getResources
	 * @see org.springframework.core.io.support.PathMatchingResourcePatternResolver
	 */
	protected ResourcePatternResolver getResourcePatternResolver() {
		return new PathMatchingResourcePatternResolver(this);
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableApplicationContext interface
	//---------------------------------------------------------------------

	/**
	 * Set the parent of this application context.
	 * <p>The parent {@linkplain ApplicationContext#getEnvironment() environment} is
	 * {@linkplain ConfigurableEnvironment#merge(ConfigurableEnvironment) merged} with
	 * this (child) application context environment if the parent is non-{@code null} and
	 * its environment is an instance of {@link ConfigurableEnvironment}.
	 * @see ConfigurableEnvironment#merge(ConfigurableEnvironment)
	 */
	@Override
	public void setParent(@Nullable ApplicationContext parent) {
		this.parent = parent;
		if (parent != null) {
			Environment parentEnvironment = parent.getEnvironment();
			if (parentEnvironment instanceof ConfigurableEnvironment) {
				getEnvironment().merge((ConfigurableEnvironment) parentEnvironment);
			}
		}
	}

	@Override
	public void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor) {
		Assert.notNull(postProcessor, "BeanFactoryPostProcessor must not be null");
		this.beanFactoryPostProcessors.add(postProcessor);
	}


	/**
	 * Return the list of BeanFactoryPostProcessors that will get applied
	 * to the internal BeanFactory.
	 */
	public List<BeanFactoryPostProcessor> getBeanFactoryPostProcessors() {
		return this.beanFactoryPostProcessors;
	}

	@Override
	public void addApplicationListener(ApplicationListener<?> listener) {
		Assert.notNull(listener, "ApplicationListener must not be null");
		if (this.applicationEventMulticaster != null) {
			this.applicationEventMulticaster.addApplicationListener(listener);
		}
		else {
			this.applicationListeners.add(listener);
		}
	}

	/**
	 * Return the list of statically specified ApplicationListeners.
	 */
	public Collection<ApplicationListener<?>> getApplicationListeners() {
		return this.applicationListeners;
	}

	/**
	 *
	 * @throws BeansException
	 * @throws IllegalStateException
	 * todo 完成IoC容器的创建及初始化工作
	 */
	@Override
	public void refresh() throws BeansException, IllegalStateException {
		synchronized (this.startupShutdownMonitor) {
			// Prepare this context for refreshing.
            //todo STEP 1： 刷新预处理
			//todo 准备刷新的上下文环境
			//todo (1)初始化前的准备工作，例如对系统属性或者环境变量进行准备及验证
			//todo 在某种情况下项目的使用需要读取某些系统变量，而这个变量的设置很可能会影响者系统的正确性，
			//todo 那么ClassPathXmlApplicationContext为我们提供的这个准备函数就显得非常必要，
			//todo  它可以在Spring启动的时候提前对必须的变量进行存在性验证
			prepareRefresh();

			// Tell the subclass to refresh the internal bean factory.
            //todo STEP 2：
            //todo 		a） 创建IoC容器（DefaultListableBeanFactory）
            //todo		b） 加载解析XML文件（最终存储到Document对象中）
            //todo		c） 读取Document对象，并完成BeanDefinition的加载和注册工作
			//todo (2 )初始化BeanFactory,并进行XML文件读取
			//todo ClassPathXmlApplicationContext包含着BeanFactory所提供的一切特征，那么在这一步骤中将会复用BeanFactory
			//todo 中的配置文件读取解析及其他功能，这一步之后，ClassPathXmlApplicationContext实际上就已经包含
			// todo BeanFactory所提供的功能，也就是可以进行Bean的提取等基础操作了
			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

			// Prepare the bean factory for use in this context.
            //todo STEP 3： 对IoC容器进行一些预处理（设置一些公共属性）
			//todo (3)对BeanFactory进行各种功能填充
			//todo @Qualifier与@Autowired 注解・这两个注解正是在这一步骤中増加的支持
			prepareBeanFactory(beanFactory);

			try {
				// Allows post-processing of the bean factory in context subclasses.
                //todo STEP 4：  子类覆盖方法做额外的处理
				//todo 提供了一个空的实数实现postProcessBeanFactory来方便在业务上做进一步扩展
				postProcessBeanFactory(beanFactory);

				// Invoke factory processors registered as beans in the context.
				//todo  STEP 5： 调用BeanFactoryPostProcessor后置处理器对BeanDefinition处理
				//todo (5)激活各种BeanFactory处理器
                invokeBeanFactoryPostProcessors(beanFactory);

				// Register bean processors that intercept bean creation.
				//todo STEP 6： 注册BeanPostProcessor后置处理器
				//todo 注册拦截bean创建的bean处理器,这里只是注册真正的调用是在getBean时候
                registerBeanPostProcessors(beanFactory);

				// Initialize message source for this context.
				//todo STEP 7： 初始化一些消息源（比如处理国际化的i18n等消息源）
				//todo 为上下文初始化Message源，即不同语言的消息体，国际化处理
                initMessageSource();

				// Initialize event multicaster for this context.
				//todo STEP 8： 初始化应用事件广播器
				//todo 初始化应用消息广播器，并放入applicationEventMulticaster, bean中
                initApplicationEventMulticaster();

				// Initialize other special beans in specific context subclasses.
				//todo STEP 9： 初始化一些特殊的bean
				//todo 留给子类来初始化其它的Bean
                onRefresh();

				// Check for listener beans and register them.
				//todo STEP 10： 注册一些监听器
				//todo 在所有注册的bean中査找Listenerbean,注册到消息广播器中
                registerListeners();

				// Instantiate all remaining (non-lazy-init) singletons.
				//todo STEP 11： 实例化剩余的单例bean（非懒加载方式）
                //todo 注意事项：Bean的IoC、DI和AOP都是发生在此步骤
				//todo 初始化剰下的单实例(非情性的)
                finishBeanFactoryInitialization(beanFactory);

				// Last step: publish corresponding event.
				//todo  STEP 12： 完成刷新时，需要发布对应的事件
				//todo 完成刷新过程.通知生命周期处埋器lifecycleProcessor刷新过程.同时发岀 ContextRefreshEvent 通知别人
                finishRefresh();
			}

			catch (BeansException ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Exception encountered during context initialization - " +
							"cancelling refresh attempt: " + ex);
				}

				// Destroy already created singletons to avoid dangling resources.
				destroyBeans();

				// Reset 'active' flag.
				cancelRefresh(ex);

				// Propagate exception to caller.
				throw ex;
			}

			finally {
				// Reset common introspection caches in Spring's core, since we
				// might not ever need metadata for singleton beans anymore...
				resetCommonCaches();
			}
		}
	}

	/**
	 * Prepare this context for refreshing, setting its startup date and
	 * active flag as well as performing any initialization of property sources.
	 * todo 系统属性及环境变量的初始化及验证
	 */
	protected void prepareRefresh() {
		this.startupDate = System.currentTimeMillis();
		this.closed.set(false);
		this.active.set(true);

		if (logger.isInfoEnabled()) {
			logger.info("Refreshing " + this);
		}

		// Initialize any placeholder property sources in the context environment
		//todo 留给子类覆盖
		//todo 根据自身的需要重写initPropertySources方法，并在方法中进行个性化的属性处理及设置
		initPropertySources();

		// Validate that all properties marked as required are resolvable
		// see ConfigurablePropertyResolver#setRequiredProperties
		//todo 验证需要的属性文件是否都巳经放入环境中 （可以自定义验证需求不存在会报错getEnvironment().setRequiredProperties("VAR")）
		getEnvironment().validateRequiredProperties();

		// Allow for the collection of early ApplicationEvents,
		// to be published once the multicaster is available...
		this.earlyApplicationEvents = new LinkedHashSet<>();
	}

	/**
	 * <p>Replace any stub property sources with actual instances.
	 * @see org.springframework.core.env.PropertySource.StubPropertySource
	 * @see org.springframework.web.context.support.WebApplicationContextUtils#initServletPropertySources
	 */
	protected void initPropertySources() {
		// For subclasses: do nothing by default.
	}

	/**
	 * Tell the subclass to refresh the internal bean factory.
	 * @return the fresh BeanFactory instance
	 * @see #refreshBeanFactory()
	 * @see #getBeanFactory()
	 */
	protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
		//todo 主要是通过该方法完成IoC容器的刷新
		//todo 初始化BeanFactory并进行XML文件读取，并将得到的BeanFacotry记录在当前实体的属性中
		//todo 方法中将核心实现委托给了 在AbstractRefreshableApplicationContext 的 方法refreshBeanFactory
		refreshBeanFactory();
		//todo 返回当前实体的beanFactory属性
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (logger.isDebugEnabled()) {
			logger.debug("Bean factory for " + getDisplayName() + ": " + beanFactory);
		}
		return beanFactory;
	}

	/**
	 * Configure the factory's standard context characteristics,
	 * such as the context's ClassLoader and post-processors.
	 * @param beanFactory the BeanFactory to configure
	 * todo 进入函数prepareBeanFactory前，Spring已经完成了对配置的解析
	 */
	protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		// Tell the internal bean factory to use the context's class loader etc.
		//todo 设置 beanFactory 的 classLoader 为当context 的 classLoader
		beanFactory.setBeanClassLoader(getClassLoader());
		//todo 设置beanFactory的表达式语言处理器.Spring3増加了表达式语言的支持默认可以使用#(bean. xxx}的形式来调用相关属性值
		//todo 1 增加对SPEL语言的支持
		//todo  Spring会调用 AbstractAutowircCapableBeanFactory类的 applyPropertyValues函数来完成功能。就在这个函数中，
		//todo 会通过构造BeaiiDefinitionValueResolver类型实例valueResolver来进行属性值的解析.
		//todo 同时，也是在这个步骤中一般通过 AbstractBeanFactory 中的 evaluateBeanDefinitionString方法去完成SPEL的解析
		beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
		//todo 为beanFactory増加了一个默认的propertyEditor,这个主要是対bean的属性等设置管理的一个工具
		//todo 2増加对属性编辑器的支持
		//todo 使用自定义属性编辑器，通过继承PropertyEditorSupport,重写setAsText方法
		//todo 在配置文件中引入类型为 org.Springffamework.beans.factory.config.CustomEditorConflgurer
		//todo 的bean,并在属性customEditors中加入自定义的属性编辑器，其中key为属性编辑器所对应 的类型。通过这样的配置，
		//todo 当Spring在注入bean的属性时一旦遇到了 java.util.Date类型的属 性会自动调用自定义的DatePropertyEditor解析
		//todo 器进行解析，并用解析结果代替配置属性进行注入
		//todo <bean class=”org.Springframework.beans.factory.config.CustomEditorConfigurer“> <property name-wcustomEditorsw>
		//todo<map>
		//todo<entry key=“java.util.Date”>
		//todo<bean class="com.test・DatePropertyEditorR>
		//todo				<property name=“format" value="yyyy-MM-dd"/>
		//todo</bean>
		//todo</entry>
		//todo</map>
		//todo</property>
		//todo</bean>
		//todo 注册Spring自带的属性编辑器CustomDateEditor  implements PropertyEditorRegistrars
		//todo public class DatePropertyEditorRegistrar implements PropertyEditorRegistrar| public void registerCustomEditors(PropertyEditorRegistry registry) (
		//todo registry. registerCustomEditor (Date.class, new CustcnDateEdxtQr(new SirnpleDateFormat ("yyyy-HM-dd"),true))；
		//todo }
		//todo )
		//todo <bean class=Horg.Springframework,beans•factory.config・Cu3tomEditorConfigurerw>
//todo 〈property name=wpropertyEditorRegi3trars">
		//todo 		<list>
//todo <bean class-*1 com. teat.DatePropertyEditorRegistrarrfX/bean>
//todo </property>
//todo </bean>
		beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

		// Configure the bean factory with context callbacks.
		//todo 添加 BeanPostProcessor
		//todo 3 增加对一些内置类，比如EnvironmentAware. MessageSourceAware的信息注入
		beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
		//todo 设置了几个忽略自动装配的接口
		//todo 4设置了依赖功能可忽略的接口
		/**
		 * todo 当 Spring 将 ApplicationContextAwareProcessor 注册后，那么在 invokeAwarelnterfaces 方
		 * todo 法中间接调用的 Aware 类已经不是普通的 bean了,如 ResourceLoaderAware、
		 * todo ApplicationEventPublisherAware等,那么当然需要在Spring做bean的依赖注入的时候忽略它们
		 */
		beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
		beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
		beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
		beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);

		// BeanFactory interface not registered as resolvable type in a plain factory.
		// MessageSource registered (and found for autowiring) as a bean.
		//todo 设置了 几个自动装配的特殊规则
		//todo 5注册一些固定依赖的属性
		/**
		 *todo 当注册了依赖解析后，例如当注册了对BeanFactory.class的解析依赖后，当bean的属性注入的时候，
		 *todo 一旦检测到属性为BeanFactory类型便会将beanFactory的实例注入进去
		 */
		beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
		beanFactory.registerResolvableDependency(ResourceLoader.class, this);
		beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
		beanFactory.registerResolvableDependency(ApplicationContext.class, this);

		// Register early post-processor for detecting inner beans as ApplicationListeners.
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

		// Detect a LoadTimeWeaver and prepare for weaving, if found.
		//todo 増加对 AspectJ的支待
		//todo 6増加Aspect的支持
		if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			//todo 只有注册了 LoadTImeWeaverAwareProcessor才会激活整个Aspectj
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			// Set a temporary ClassLoader for type matching.
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}

		// Register default environment beans.
		//todo 添加默认的系统环境bean
		//todo 7将相关环境变量及属性注册以单例模式注册
		if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
		}
	}

	/**
	 * Modify the application context's internal bean factory after its standard
	 * initialization. All bean definitions will have been loaded, but no beans
	 * will have been instantiated yet. This allows for registering special
	 * BeanPostProcessors etc in certain ApplicationContext implementations.
	 * @param beanFactory the bean factory used by the application context
	 * todo BeanFacotry作为Spring中容器功能的基础，用于存放所有已经加载的bean,为了保证程序上的髙可扩展性，
*      todo Spring针对BeanFactory做了大量的扩展，比如我们熟知的PostProcessor等都是在这里实现的
	 */
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
	}

	/**
	 * Instantiate and invoke all registered BeanFactoryPostProcessor beans,
	 * respecting explicit order if given.
	 * <p>Must be called before singleton instantiation.
	 * todo Spring中存在对于BeanFactoryPostProcessor的典型应用，比如PropertyPlaceholderConfigurer
	 * todo 查看层级结构可以看出PropertyPlaceholderConfigurer这个类间接继承了BeanFactoryPostprocessor接口。
	 * todo 这是一个很特别的接口，当Spring加载任何实现了这个接口的bean的配置时，都会在bean工厂载入所有bean的配置之后执行
	 * todo postProcessBeanFactory方法。在 PropertyResourceConfigurer 类中实现postProcessBeanFactory 方法，
	 * todo 在方法中先后调用了mergeProperties. convertProperties. processProperties 这 3 个方法，分別得到配置，
	 * todo 将得到的配置转换为合适的类型，最后将配置内容告知BeanFactory
	 * todo 使用自定义 BeanFactoryPostProcessor 通过postProcessBeanFactory()方法可以去除潜在的“流氓”属性值
	 */
	protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		//todo 委托给invokeBeanFactoryPostProcessors()方法
		PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

		// Detect a LoadTimeWeaver and prepare for weaving, if found in the meantime
		// (e.g. through an @Bean method registered by ConfigurationClassPostProcessor)
		if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}
	}

	/**
	 * Instantiate and invoke all registered BeanPostProcessor beans,
	 * respecting explicit order if given.
	 * <p>Must be called before any instantiation of application beans.
	 * todo  implements InstantiationAwareBeanPostprocessor 可以打印bena 只有Applicationcontext方式获取bean才生效
	 */
	protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		//todo 委托给 PostProcessorRegistrationDelegate 的registerBeanPostProcessors方法
		PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
	}

	/**
	 * Initialize the MessageSource.
	 * Use parent's if none defined in this context.
	 * todo 初始化消息资源
	 * todo Java通过 java.util.Locale类表示一个本地化对象，它允许通过语言参数和国家/地区参数创建一个确定的本地化对象
	 *   带有语言和国家/地区值息的本地化对象
	     Locale localel = new Locale ("zh","CN");
	     只有语言信息的本地化对象
	     Locale locale2 = new Locale ("zh“);
	    等同于Locale ("zh","CN")
	 	Locale locale3 = Locale.CHINA;
	 	等同于 Locale ("zh“)
	 	Locale locale4 = Locale.CHINESE;
	 	获取本地系统默认的本地化对象
	 	Locale locale 5= Locale.getDefault();
	 JDK的java.util包中提供了几个支持本地化的格式化操作工具类：NumberFormat. DateFormat. MessageFormat,
	 而在Spring中的国际化资源操作也无非是对于这些类的封装操作
	 ResourceBundleMessageSource方式实现
	 (1 )定义资源文件
	 messages.properties (默认：英文)，内容仅一句，如下：
	 test-test
	 。 messages_zh_CN.properties (简体中文)：
	 test=测试
	 然后 cmd ,打开命令行窗口，输入 native2ascii -encoding gbk C:\mcssagcs zh CN.propcrties C:\messages_zh_CN
	 tem.properliest 并将 C:\messages zh CNjem.properties 中的内容替换到 messages zh CN.properties中，这样messages
	 zh CN.properties文件就存放的是转码后的内容 了
	 （2）定义配置文件
	 <bean id="messageSource" class="org.Springframework. context. support. ResourceBundleMessageSource">
	 <property name="basenames">
	 <list>
	 <value>test/messages</value>
	 </property>
	 </bean>
	 其中，这个Bean的ID必须命名为messageSource,否则会抛出NoSuchMessageException 异常
	 todo 在initMessageSource中的方法主要功能是提取配置:中定义的messageSource,并将其记录 在Spring的容器中，
	 todo 也就是AbstractAppiicationContext中。当然，如果用户未设置资源文件的 话，
	 todo Spring 中也提供了默认的配置 DelegatingMessageSource
	 */
	protected void initMessageSource() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)) {
			//todo 如果在配置中已经配置了messagesource那么将messagesource提取并记录在 this.messagesource 中
			this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
			// Make MessageSource aware of parent MessageSource.
			if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource) {
				HierarchicalMessageSource hms = (HierarchicalMessageSource) this.messageSource;
				if (hms.getParentMessageSource() == null) {
					// Only set parent context as parent MessageSource if no parent MessageSource
					// registered already.
					hms.setParentMessageSource(getInternalParentMessageSource());
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Using MessageSource [" + this.messageSource + "]");
			}
		}
		else {
			// Use empty MessageSource to be able to accept getMessage calls.
			//todo 如果用户并没有定义配置文件.那么使用临时的DelegatingMessageSoarce以便于作为调用 getMessage方法的返回
			DelegatingMessageSource dms = new DelegatingMessageSource();
			dms.setParentMessageSource(getInternalParentMessageSource());
			this.messageSource = dms;
			beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate MessageSource with name '" + MESSAGE_SOURCE_BEAN_NAME +
						"': using default [" + this.messageSource + "]");
			}
		}
	}

	/**
	 * Initialize the ApplicationEventMulticaster.
	 * Uses SimpleApplicationEventMulticaster if none defined in the context.
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 * todo 事件传播器
	 *
	 * public class TestEvent extends ApplicationEvent{
	 *
	 * }
	 * public class TestListener implements ApplicationListener{
	 *
	 *     public void onApplicationEvent(ApplicationEvent event){
	 *         if(event instanceof TestEvent){
	 *             TestEvent testEvent= (TestEvent)event;
	 *             testEvent.print();
	 *         }
	 *     }
	 * }
	 * <bean id="testListener" class="com.test.event.TestListener">
	 *     context.publishEvent(event);
	 */
	protected void initApplicationEventMulticaster() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
			//todo 如果用户自定义了事件广播器，那么使用用户自定义的事件广播器
			this.applicationEventMulticaster =
					beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using ApplicationEventMulticaster [" + this.applicationEventMulticaster + "]");
			}
		}
		else {
			//todo 默认的广播器实现
			this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
			beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate ApplicationEventMulticaster with name '" +
						APPLICATION_EVENT_MULTICASTER_BEAN_NAME +
						"': using default [" + this.applicationEventMulticaster + "]");
			}
		}
	}

	/**
	 * Initialize the LifecycleProcessor.
	 * Uses DefaultLifecycleProcessor if none defined in the context.
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 * todo 当ApplicationContcxt启动或停止时，它会通过LifecycleProcessor来与所有声明的bean的
	    todo  周期做状态更新.而在LifbcycleProcessor的使用前首先需要初始化
	 */
	protected void initLifecycleProcessor() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(LIFECYCLE_PROCESSOR_BEAN_NAME)) {
			this.lifecycleProcessor =
					beanFactory.getBean(LIFECYCLE_PROCESSOR_BEAN_NAME, LifecycleProcessor.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using LifecycleProcessor [" + this.lifecycleProcessor + "]");
			}
		}
		else {
			DefaultLifecycleProcessor defaultProcessor = new DefaultLifecycleProcessor();
			defaultProcessor.setBeanFactory(beanFactory);
			this.lifecycleProcessor = defaultProcessor;
			beanFactory.registerSingleton(LIFECYCLE_PROCESSOR_BEAN_NAME, this.lifecycleProcessor);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate LifecycleProcessor with name '" +
						LIFECYCLE_PROCESSOR_BEAN_NAME +
						"': using default [" + this.lifecycleProcessor + "]");
			}
		}
	}

	/**
	 * Template method which can be overridden to add context-specific refresh work.
	 * Called on initialization of special beans, before instantiation of singletons.
	 * <p>This implementation is empty.
	 * @throws BeansException in case of errors
	 * @see #refresh()
	 */
	protected void onRefresh() throws BeansException {
		// For subclasses: do nothing by default.
	}

	/**
	 * Add beans that implement ApplicationListener as listeners.
	 * Doesn't affect other listeners, which can be added without being beans.
	 * todo 注册Listenerbean
	 *
	 */
	protected void registerListeners() {
		// Register statically specified listeners first.
		//todo 硬编码方式注册的监听器处理
		for (ApplicationListener<?> listener : getApplicationListeners()) {
			getApplicationEventMulticaster().addApplicationListener(listener);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let post-processors apply to them!
		//todo 配置文件注册的监听器处理
		String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
		for (String listenerBeanName : listenerBeanNames) {
			getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
		}

		// Publish early application events now that we finally have a multicaster...
		Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
		this.earlyApplicationEvents = null;
		if (earlyEventsToProcess != null) {
			for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
				getApplicationEventMulticaster().multicastEvent(earlyEvent);
			}
		}
	}

	/**
	 * Finish the initialization of this context's bean factory,
	 * initializing all remaining singleton beans.
	 * todo 完成BeanFactory的初始化工作，其中包括ConversionService的设置、配置冻结以及非延迟加载的bean的初始化工作
	 */
	protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
		// Initialize conversion service for this context.
		//todo 1.ConversionService 的设置
		//todo (1)定义转换器。
		//todo public class String2DateConverter implements Converter<String, Date> (
		//todo (2)注册。
		//todo <bean id=“conversionService” class="org.Springframework.context.support.ConversionServiceFactoryBean">
		//todo <property name="converters">
		//todo <list>
		//todo<bean class=RString2DateCanvertern />
		//todo </list>
		//todo</property>
		//todo</bean>
		if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
				beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
			beanFactory.setConversionService(
					beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
		}

		// Register a default embedded value resolver if no bean post-processor
		// (such as a PropertyPlaceholderConfigurer bean) registered any before:
		// at this point, primarily for resolution in annotation attribute values.
		if (!beanFactory.hasEmbeddedValueResolver()) {
			beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
		}

		// Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
		String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
		for (String weaverAwareName : weaverAwareNames) {
			getBean(weaverAwareName);
		}

		// Stop using the temporary ClassLoader for type matching.
		beanFactory.setTempClassLoader(null);

		// Allow for caching all bean definition metadata, not expecting further changes.
		//todo 冻结所有的bean定义.说明注册的bean定义将不被修改或任何进一步的处理
		beanFactory.freezeConfiguration();

		// Instantiate all remaining (non-lazy-init) singletons.
		//todo  实例化单例Bean 初始化剰下的单实例(非情性的)
		beanFactory.preInstantiateSingletons();
	}

	/**
	 * Finish the refresh of this context, invoking the LifecycleProcessor's
	 * onRefresh() method and publishing the
	 * {@link org.springframework.context.event.ContextRefreshedEvent}.
	 * todo 在Spring中还提供了 Lifecycle接曰，Lifecycle中包含start/stop方法，
	 * todo 实现此接口后Spring会 保证在启动的时候调用其start方法开始牛命周期，并在Spring关闭的时候调用stop方法来结束生命周期，
	 * todo 通常用来配置后台程序，在启动后一直运行(如对MQ进行轮询等)。而ApplicationContext 的初始化最后正是保证了这一功能的实现
	 */
	protected void finishRefresh() {
		// Clear context-level resource caches (such as ASM metadata from scanning).
		clearResourceCaches();

		// Initialize lifecycle processor for this context.
		//todo 当ApplicationContcxt启动或停止时，它会通过LifecycleProcessor来与所有声明的bean的
		//todo  周期做状态更新.而在LifbcycleProcessor的使用前首先需要初始化
		initLifecycleProcessor();

		// Propagate refresh to lifecycle processor first.
		//todo 启动所有实现了 Lifecycle接口的bean
		getLifecycleProcessor().onRefresh();

		// Publish the final event.
		//todo  当完成Applicationcontext初始化的时候，要通过Spring中的事件发布机制来发出ContextRefreshedEvent事件，
		//todo 以保证对应的监听器可以做进一步的逻辑处理
		publishEvent(new ContextRefreshedEvent(this));

		// Participate in LiveBeansView MBean, if active.
		LiveBeansView.registerApplicationContext(this);
	}

	/**
	 * Cancel this context's refresh attempt, resetting the {@code active} flag
	 * after an exception got thrown.
	 * @param ex the exception that led to the cancellation
	 */
	protected void cancelRefresh(BeansException ex) {
		this.active.set(false);
	}

	/**
	 * Reset Spring's common reflection metadata caches, in particular the
	 * {@link ReflectionUtils}, {@link AnnotationUtils}, {@link ResolvableType}
	 * and {@link CachedIntrospectionResults} caches.
	 * @since 4.2
	 * @see ReflectionUtils#clearCache()
	 * @see AnnotationUtils#clearCache()
	 * @see ResolvableType#clearCache()
	 * @see CachedIntrospectionResults#clearClassLoader(ClassLoader)
	 */
	protected void resetCommonCaches() {
		ReflectionUtils.clearCache();
		AnnotationUtils.clearCache();
		ResolvableType.clearCache();
		CachedIntrospectionResults.clearClassLoader(getClassLoader());
	}


	/**
	 * Register a shutdown hook with the JVM runtime, closing this context
	 * on JVM shutdown unless it has already been closed at that time.
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 * @see Runtime#addShutdownHook
	 * @see #close()
	 * @see #doClose()
	 */
	@Override
	public void registerShutdownHook() {
		if (this.shutdownHook == null) {
			// No shutdown hook registered yet.
			this.shutdownHook = new Thread() {
				@Override
				public void run() {
					synchronized (startupShutdownMonitor) {
						doClose();
					}
				}
			};
			Runtime.getRuntime().addShutdownHook(this.shutdownHook);
		}
	}

	/**
	 * Callback for destruction of this instance, originally attached
	 * to a {@code DisposableBean} implementation (not anymore in 5.0).
	 * <p>The {@link #close()} method is the native way to shut down
	 * an ApplicationContext, which this method simply delegates to.
	 * @deprecated as of Spring Framework 5.0, in favor of {@link #close()}
	 */
	@Deprecated
	public void destroy() {
		close();
	}

	/**
	 * Close this application context, destroying all beans in its bean factory.
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 * Also removes a JVM shutdown hook, if registered, as it's not needed anymore.
	 * @see #doClose()
	 * @see #registerShutdownHook()
	 */
	@Override
	public void close() {
		synchronized (this.startupShutdownMonitor) {
			doClose();
			// If we registered a JVM shutdown hook, we don't need it anymore now:
			// We've already explicitly closed the context.
			if (this.shutdownHook != null) {
				try {
					Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
				}
				catch (IllegalStateException ex) {
					// ignore - VM is already shutting down
				}
			}
		}
	}

	/**
	 * Actually performs context closing: publishes a ContextClosedEvent and
	 * destroys the singletons in the bean factory of this application context.
	 * <p>Called by both {@code close()} and a JVM shutdown hook, if any.
	 * @see org.springframework.context.event.ContextClosedEvent
	 * @see #destroyBeans()
	 * @see #close()
	 * @see #registerShutdownHook()
	 */
	protected void doClose() {
		if (this.active.get() && this.closed.compareAndSet(false, true)) {
			if (logger.isInfoEnabled()) {
				logger.info("Closing " + this);
			}

			LiveBeansView.unregisterApplicationContext(this);

			try {
				// Publish shutdown event.
				publishEvent(new ContextClosedEvent(this));
			}
			catch (Throwable ex) {
				logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
			}

			// Stop all Lifecycle beans, to avoid delays during individual destruction.
			if (this.lifecycleProcessor != null) {
				try {
					this.lifecycleProcessor.onClose();
				}
				catch (Throwable ex) {
					logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
				}
			}

			// Destroy all cached singletons in the context's BeanFactory.
			destroyBeans();

			// Close the state of this context itself.
			closeBeanFactory();

			// Let subclasses do some final clean-up if they wish...
			onClose();

			this.active.set(false);
		}
	}

	/**
	 * Template method for destroying all beans that this context manages.
	 * The default implementation destroy all cached singletons in this context,
	 * invoking {@code DisposableBean.destroy()} and/or the specified
	 * "destroy-method".
	 * <p>Can be overridden to add context-specific bean destruction steps
	 * right before or right after standard singleton destruction,
	 * while the context's BeanFactory is still active.
	 * @see #getBeanFactory()
	 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#destroySingletons()
	 */
	protected void destroyBeans() {
		getBeanFactory().destroySingletons();
	}

	/**
	 * Template method which can be overridden to add context-specific shutdown work.
	 * The default implementation is empty.
	 * <p>Called at the end of {@link #doClose}'s shutdown procedure, after
	 * this context's BeanFactory has been closed. If custom shutdown logic
	 * needs to execute while the BeanFactory is still active, override
	 * the {@link #destroyBeans()} method instead.
	 */
	protected void onClose() {
		// For subclasses: do nothing by default.
	}

	@Override
	public boolean isActive() {
		return this.active.get();
	}

	/**
	 * Assert that this context's BeanFactory is currently active,
	 * throwing an {@link IllegalStateException} if it isn't.
	 * <p>Invoked by all {@link BeanFactory} delegation methods that depend
	 * on an active context, i.e. in particular all bean accessor methods.
	 * <p>The default implementation checks the {@link #isActive() 'active'} status
	 * of this context overall. May be overridden for more specific checks, or for a
	 * no-op if {@link #getBeanFactory()} itself throws an exception in such a case.
	 */
	protected void assertBeanFactoryActive() {
		if (!this.active.get()) {
			if (this.closed.get()) {
				throw new IllegalStateException(getDisplayName() + " has been closed already");
			}
			else {
				throw new IllegalStateException(getDisplayName() + " has not been refreshed yet");
			}
		}
	}


	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name);
	}

	@Override
	public <T> T getBean(String name, @Nullable Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, requiredType);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, args);
	}

	@Override
	public <T> T getBean(Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType);
	}

	@Override
	public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType, args);
	}

	@Override
	public boolean containsBean(String name) {
		return getBeanFactory().containsBean(name);
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isSingleton(name);
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isPrototype(name);
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	public boolean isTypeMatch(String name, @Nullable Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name);
	}

	@Override
	public String[] getAliases(String name) {
		return getBeanFactory().getAliases(name);
	}


	//---------------------------------------------------------------------
	// Implementation of ListableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public boolean containsBeanDefinition(String beanName) {
		return getBeanFactory().containsBeanDefinition(beanName);
	}

	@Override
	public int getBeanDefinitionCount() {
		return getBeanFactory().getBeanDefinitionCount();
	}

	@Override
	public String[] getBeanDefinitionNames() {
		return getBeanFactory().getBeanDefinitionNames();
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForAnnotation(annotationType);
	}

	@Override
	public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansWithAnnotation(annotationType);
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException{

		assertBeanFactoryActive();
		return getBeanFactory().findAnnotationOnBean(beanName, annotationType);
	}


	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return getParent();
	}

	@Override
	public boolean containsLocalBean(String name) {
		return getBeanFactory().containsLocalBean(name);
	}

	/**
	 * Return the internal bean factory of the parent context if it implements
	 * ConfigurableApplicationContext; else, return the parent context itself.
	 * @see org.springframework.context.ConfigurableApplicationContext#getBeanFactory
	 */
	@Nullable
	protected BeanFactory getInternalParentBeanFactory() {
		return (getParent() instanceof ConfigurableApplicationContext) ?
				((ConfigurableApplicationContext) getParent()).getBeanFactory() : getParent();
	}


	//---------------------------------------------------------------------
	// Implementation of MessageSource interface
	//---------------------------------------------------------------------

	/**
	 *
	 * @param code the code to lookup up, such as 'calculator.noRateSet'. Users of
	 * this class are encouraged to base message names on the relevant fully
	 * qualified class name, thus avoiding conflict and ensuring maximum clarity.
	 * @param args an array of arguments that will be filled in for params within
	 * the message (params look like "{0}", "{1,date}", "{2,time}" within a message),
	 * or {@code null} if none.
	 * @param defaultMessage a default message to return if the lookup fails
	 * @param locale the locale in which to do the lookup
	 * @return
	 * todo 获取Message
	 * todo getMessageSource 获取的是 initMessageSource 方法配置的MessageSource 自定义或者是默认的DelegatingMessageSource
	 */
	@Override
	public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, Locale locale) {
		return getMessageSource().getMessage(code, args, defaultMessage, locale);
	}

	@Override
	public String getMessage(String code, @Nullable Object[] args, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(code, args, locale);
	}

	@Override
	public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(resolvable, locale);
	}

	/**
	 * Return the internal MessageSource used by the context.
	 * @return the internal MessageSource (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	private MessageSource getMessageSource() throws IllegalStateException {
		if (this.messageSource == null) {
			throw new IllegalStateException("MessageSource not initialized - " +
					"call 'refresh' before accessing messages via the context: " + this);
		}
		return this.messageSource;
	}

	/**
	 * Return the internal message source of the parent context if it is an
	 * AbstractApplicationContext too; else, return the parent context itself.
	 */
	@Nullable
	protected MessageSource getInternalParentMessageSource() {
		return (getParent() instanceof AbstractApplicationContext) ?
			((AbstractApplicationContext) getParent()).messageSource : getParent();
	}


	//---------------------------------------------------------------------
	// Implementation of ResourcePatternResolver interface
	//---------------------------------------------------------------------

	@Override
	public Resource[] getResources(String locationPattern) throws IOException {
		return this.resourcePatternResolver.getResources(locationPattern);
	}


	//---------------------------------------------------------------------
	// Implementation of Lifecycle interface
	//---------------------------------------------------------------------

	@Override
	public void start() {
		getLifecycleProcessor().start();
		publishEvent(new ContextStartedEvent(this));
	}

	@Override
	public void stop() {
		getLifecycleProcessor().stop();
		publishEvent(new ContextStoppedEvent(this));
	}

	@Override
	public boolean isRunning() {
		return (this.lifecycleProcessor != null && this.lifecycleProcessor.isRunning());
	}


	//---------------------------------------------------------------------
	// Abstract methods that must be implemented by subclasses
	//---------------------------------------------------------------------

	/**
	 * Subclasses must implement this method to perform the actual configuration load.
	 * The method is invoked by {@link #refresh()} before any other initialization work.
	 * <p>A subclass will either create a new bean factory and hold a reference to it,
	 * or return a single BeanFactory instance that it holds. In the latter case, it will
	 * usually throw an IllegalStateException if refreshing the context more than once.
	 * @throws BeansException if initialization of the bean factory failed
	 * @throws IllegalStateException if already initialized and multiple refresh
	 * attempts are not supported
	 */
	protected abstract void refreshBeanFactory() throws BeansException, IllegalStateException;

	/**
	 * Subclasses must implement this method to release their internal bean factory.
	 * This method gets invoked by {@link #close()} after all other shutdown work.
	 * <p>Should never throw an exception but rather log shutdown failures.
	 */
	protected abstract void closeBeanFactory();

	/**
	 * Subclasses must return their internal bean factory here. They should implement the
	 * lookup efficiently, so that it can be called repeatedly without a performance penalty.
	 * <p>Note: Subclasses should check whether the context is still active before
	 * returning the internal bean factory. The internal factory should generally be
	 * considered unavailable once the context has been closed.
	 * @return this application context's internal bean factory (never {@code null})
	 * @throws IllegalStateException if the context does not hold an internal bean factory yet
	 * (usually if {@link #refresh()} has never been called) or if the context has been
	 * closed already
	 * @see #refreshBeanFactory()
	 * @see #closeBeanFactory()
	 */
	@Override
	public abstract ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;


	/**
	 * Return information about this context.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getDisplayName());
		sb.append(": startup date [").append(new Date(getStartupDate()));
		sb.append("]; ");
		ApplicationContext parent = getParent();
		if (parent == null) {
			sb.append("root of context hierarchy");
		}
		else {
			sb.append("parent: ").append(parent.getDisplayName());
		}
		return sb.toString();
	}

}
