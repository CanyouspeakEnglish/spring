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

package org.springframework.web.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.ui.context.ThemeSource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.WebUtils;

/**
 * Central dispatcher for HTTP request handlers/controllers, e.g. for web UI controllers
 * or HTTP-based remote service exporters. Dispatches to registered handlers for processing
 * a web request, providing convenient mapping and exception handling facilities.
 *
 * <p>This servlet is very flexible: It can be used with just about any workflow, with the
 * installation of the appropriate adapter classes. It offers the following functionality
 * that distinguishes it from other request-driven web MVC frameworks:
 *
 * <ul>
 * <li>It is based around a JavaBeans configuration mechanism.
 *
 * <li>It can use any {@link HandlerMapping} implementation - pre-built or provided as part
 * of an application - to control the routing of requests to handler objects. Default is
 * {@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping} and
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}.
 * HandlerMapping objects can be defined as beans in the servlet's application context,
 * implementing the HandlerMapping interface, overriding the default HandlerMapping if
 * present. HandlerMappings can be given any bean name (they are tested by type).
 *
 * <li>It can use any {@link HandlerAdapter}; this allows for using any handler interface.
 * Default adapters are {@link org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter},
 * {@link org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter}, for Spring's
 * {@link org.springframework.web.HttpRequestHandler} and
 * {@link org.springframework.web.servlet.mvc.Controller} interfaces, respectively. A default
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter}
 * will be registered as well. HandlerAdapter objects can be added as beans in the
 * application context, overriding the default HandlerAdapters. Like HandlerMappings,
 * HandlerAdapters can be given any bean name (they are tested by type).
 *
 * <li>The dispatcher's exception resolution strategy can be specified via a
 * {@link HandlerExceptionResolver}, for example mapping certain exceptions to error pages.
 * Default are
 * {@link org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver},
 * {@link org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver}, and
 * {@link org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver}.
 * These HandlerExceptionResolvers can be overridden through the application context.
 * HandlerExceptionResolver can be given any bean name (they are tested by type).
 *
 * <li>Its view resolution strategy can be specified via a {@link ViewResolver}
 * implementation, resolving symbolic view names into View objects. Default is
 * {@link org.springframework.web.servlet.view.InternalResourceViewResolver}.
 * ViewResolver objects can be added as beans in the application context, overriding the
 * default ViewResolver. ViewResolvers can be given any bean name (they are tested by type).
 *
 * <li>If a {@link View} or view name is not supplied by the user, then the configured
 * {@link RequestToViewNameTranslator} will translate the current request into a view name.
 * The corresponding bean name is "viewNameTranslator"; the default is
 * {@link org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator}.
 *
 * <li>The dispatcher's strategy for resolving multipart requests is determined by a
 * {@link org.springframework.web.multipart.MultipartResolver} implementation.
 * Implementations for Apache Commons FileUpload and Servlet 3 are included; the typical
 * choice is {@link org.springframework.web.multipart.commons.CommonsMultipartResolver}.
 * The MultipartResolver bean name is "multipartResolver"; default is none.
 *
 * <li>Its locale resolution strategy is determined by a {@link LocaleResolver}.
 * Out-of-the-box implementations work via HTTP accept header, cookie, or session.
 * The LocaleResolver bean name is "localeResolver"; default is
 * {@link org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver}.
 *
 * <li>Its theme resolution strategy is determined by a {@link ThemeResolver}.
 * Implementations for a fixed theme and for cookie and session storage are included.
 * The ThemeResolver bean name is "themeResolver"; default is
 * {@link org.springframework.web.servlet.theme.FixedThemeResolver}.
 * </ul>
 *
 * <p><b>NOTE: The {@code @RequestMapping} annotation will only be processed if a
 * corresponding {@code HandlerMapping} (for type-level annotations) and/or
 * {@code HandlerAdapter} (for method-level annotations) is present in the dispatcher.</b>
 * This is the case by default. However, if you are defining custom {@code HandlerMappings}
 * or {@code HandlerAdapters}, then you need to make sure that a corresponding custom
 * {@code RequestMappingHandlerMapping} and/or {@code RequestMappingHandlerAdapter}
 * is defined as well - provided that you intend to use {@code @RequestMapping}.
 *
 * <p><b>A web application can define any number of DispatcherServlets.</b>
 * Each servlet will operate in its own namespace, loading its own application context
 * with mappings, handlers, etc. Only the root application context as loaded by
 * {@link org.springframework.web.context.ContextLoaderListener}, if any, will be shared.
 *
 * <p>As of Spring 3.1, {@code DispatcherServlet} may now be injected with a web
 * application context, rather than creating its own internally. This is useful in Servlet
 * 3.0+ environments, which support programmatic registration of servlet instances.
 * See the {@link #DispatcherServlet(WebApplicationContext)} javadoc for details.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Chris Beams
 * @author Rossen Stoyanchev
 * @see org.springframework.web.HttpRequestHandler
   init * @see org.springframework.web.context.ContextLoaderListener
 	todo SpringMVC的前端控制器
 	todo 当DispatcherServlet 载入后,它将从一个XML文件中载入Spring的应用上下文,该XML文件的名字取决于<servlet-name>
 	todo 这里DispatcherServlet将试图从一个叫做Springmvc-servlet.xml的文件中载入应用上下文,其中默认位于WEB-INF目录下
 	<servlet>
		<servlet>Springmvc</servlet>
		<servlet-class>org.Springframework.web.servlet.DispatcherServlet</servlet-class>
		<load-on-startup>l</load-on-startup>
    </servlet>
	<servlet-mapping>
		<servlet-name>Springmvc</servlet-name>
		<url-pattern>*.html</url-pattern>
	</servlet-mapping>
	<!--配置上下文载入器-->
	<!--上下文载入器载入除DispatcherServlet载入的配置文件之外的其他上下文配置文件-->
	<!--最常用的上下文载入器是一个Servlet监听器,其名称为ContextLoaderListener-->
 	<listener>
		<listener-class>org.Springframework.web.context.ContextLoaderListener</listener-class>
    </listener>
 	Spring的mvc之所以必须配置web.xml,其实最关键的是要配置两个地方
 	contextConfigLocation:Spring的核心就是配置文件,可以说配置文件是Spring中必不可少的东西,而这个参数就是使Web与Spring的配置文件相
 	结合的一个关键配置
	DispatcherServlet:包含了SpringMVC的请求逻辑,Spring使用此类拦截Web请求并进行相应的逻辑处理
 	<bean id="viewResolver" class="org.Springframework.web.servlet.view.InternalResourceViewResolver">
		<property name="prefix" value="/WEB-INF/jsp/"/>
		<property name="suffix" value=". jsp"/>
	</bean>
	InternalResourceViewResolver是一个辅助Bean,会在ModeiAndView返回的视图名前加上 prefix指定的前缀，
    再在最后加上suffix指定的后缀，例如：由于XXController返回的 ModeiAndView中的视图名是testview,
    故该视图解析器将在/WEB-INF/jsp/testview.jsp处査找 视图
<bean id=wsimpleUrLMappingM class=Morg.Springframework.web.servlet.handler.SimpleUrlHandlerMappingw>
<property name="mappings"
	<props>
		<prop key="/userList.htm">userController</prop>
	</props>
</property>
</bean>
< !-- 这里的 id="userCont roll色r”对应的是〈bean id=ttsinipleUrlMappingft>^ 的<prop> 里面的 value ■・>
<bean id="userController" class="test.controller.UserController" />
 todo DispatcherServIet是实现servlet 接口的实现类
 todo servlet是一个Java编写的程序，此程序是基于HTTP协议的，在服务器端运行的（如 Tomcat）,是按照servlet规范编写的一个Java类
 todo 。主要是处理客户端的请求并将其结果发送到客户端.servlet的生命周期是由servlet的容器来控制的,它可以分为3个阶段：初始化,运行和销毁
 todo (1) 初始化阶段
 todo servlet容器加载servlet类，把servlet类的.class文件中的数据读到内存中
 todo servlet容器创建一个ServletConfig对象,ServletConfig对象包含了servlet的初始化配置信息
 todo servlet容器创建一个servlet对象
 todo servlet容器调用servlet对象的init方法进行初始化
 todo （2）	运行阶段
 todo 当servlet容器接收到一个请求时，servlet容器会针对这个请求创建servletRequest和 servletResponse对象，
 todo 然后调用service方法。并把这两个参数传递给service方法。service方 法通过servletRequest对象获得请求的信息。
 todo 并处理该请求。再通过servletResponse对象生成 这个请求的响应结果。然后销毁servletRequest和servletResponse对象
 todo 。我们不管这个请求是 post提交的还是get提交的，最终这个请求都会由service方法来处理
 todo （3）	销毁阶段
 todo 当Web应用被终止时.servlet容器会先调用servlet对象的destrory方法，然后再销毁servlet 对象，同时也会销毁与servlet对象相关联
 todo 的servletConfig对象。我们可以在destroy方法的实 现中，释放servlet所占用的资源，如关闭数据库连接，关闭文件输入输出流等
 todo servlet 的框架是由两个 Java 包组成：javax.servlet 和 javax.servlet.http。在 javax.servlet 包中定义了所有的servlet
 todo 类都必须实现或扩展的通用接口和类，在javax.servlet.http包中定义 了采用HTTP通信协议的HttpServlet类
 todo servlet被设计成请求驱动，servlet的请求可能包含多个数据项，当Web容器接收到某个 servlet请求时，servlet把请求封装成一个HttpServletRequest对象，
 todo 然后把对象传给servlet的对应的服务方法
 todo HTTP 的请求方式包括 deletex get、optionspost、put 和 trace,在 HttpServlet 类中分别提
 todo 供了相应的服务方法，它们是 doDelete(). doGet。、doOptions() doPost(). doPut()和 doTrace()

 */
@SuppressWarnings("serial")
public class DispatcherServlet extends FrameworkServlet {

	/** Well-known name for the MultipartResolver object in the bean factory for this namespace. */
	public static final String MULTIPART_RESOLVER_BEAN_NAME = "multipartResolver";

	/** Well-known name for the LocaleResolver object in the bean factory for this namespace. */
	public static final String LOCALE_RESOLVER_BEAN_NAME = "localeResolver";

	/** Well-known name for the ThemeResolver object in the bean factory for this namespace. */
	public static final String THEME_RESOLVER_BEAN_NAME = "themeResolver";

	/**
	 * Well-known name for the HandlerMapping object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerMappings" is turned off.
	 * @see #setDetectAllHandlerMappings
	 */
	public static final String HANDLER_MAPPING_BEAN_NAME = "handlerMapping";

	/**
	 * Well-known name for the HandlerAdapter object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerAdapters" is turned off.
	 * @see #setDetectAllHandlerAdapters
	 */
	public static final String HANDLER_ADAPTER_BEAN_NAME = "handlerAdapter";

	/**
	 * Well-known name for the HandlerExceptionResolver object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerExceptionResolvers" is turned off.
	 * @see #setDetectAllHandlerExceptionResolvers
	 */
	public static final String HANDLER_EXCEPTION_RESOLVER_BEAN_NAME = "handlerExceptionResolver";

	/**
	 * Well-known name for the RequestToViewNameTranslator object in the bean factory for this namespace.
	 */
	public static final String REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME = "viewNameTranslator";

	/**
	 * Well-known name for the ViewResolver object in the bean factory for this namespace.
	 * Only used when "detectAllViewResolvers" is turned off.
	 * @see #setDetectAllViewResolvers
	 */
	public static final String VIEW_RESOLVER_BEAN_NAME = "viewResolver";

	/**
	 * Well-known name for the FlashMapManager object in the bean factory for this namespace.
	 */
	public static final String FLASH_MAP_MANAGER_BEAN_NAME = "flashMapManager";

	/**
	 * Request attribute to hold the current web application context.
	 * Otherwise only the global web app context is obtainable by tags etc.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#findWebApplicationContext
	 */
	public static final String WEB_APPLICATION_CONTEXT_ATTRIBUTE = DispatcherServlet.class.getName() + ".CONTEXT";

	/**
	 * Request attribute to hold the current LocaleResolver, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getLocaleResolver
	 */
	public static final String LOCALE_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".LOCALE_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeResolver, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeResolver
	 */
	public static final String THEME_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeSource, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeSource
	 */
	public static final String THEME_SOURCE_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_SOURCE";

	/**
	 * Name of request attribute that holds a read-only {@code Map<String,?>}
	 * with "input" flash attributes saved by a previous request, if any.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getInputFlashMap(HttpServletRequest)
	 */
	public static final String INPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".INPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the "output" {@link FlashMap} with
	 * attributes to save for a subsequent request.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getOutputFlashMap(HttpServletRequest)
	 */
	public static final String OUTPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".OUTPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the {@link FlashMapManager}.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getFlashMapManager(HttpServletRequest)
	 */
	public static final String FLASH_MAP_MANAGER_ATTRIBUTE = DispatcherServlet.class.getName() + ".FLASH_MAP_MANAGER";

	/**
	 * Name of request attribute that exposes an Exception resolved with an
	 * {@link HandlerExceptionResolver} but where no view was rendered
	 * (e.g. setting the status code).
	 */
	public static final String EXCEPTION_ATTRIBUTE = DispatcherServlet.class.getName() + ".EXCEPTION";

	/** Log category to use when no mapped handler is found for a request. */
	public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

	/**
	 * Name of the class path resource (relative to the DispatcherServlet class)
	 * that defines DispatcherServlet's default strategy names.
	 */
	private static final String DEFAULT_STRATEGIES_PATH = "DispatcherServlet.properties";

	/**
	 * Common prefix that DispatcherServlet's default strategy attributes start with.
	 */
	private static final String DEFAULT_STRATEGIES_PREFIX = "org.springframework.web.servlet";

	/** Additional logger to use when no mapped handler is found for a request. */
	protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);

	private static final Properties defaultStrategies;
	//todo 在系统加载的时候，defaultStrategies根据当前路径DispatcherServlet.properties来初始化本 身，
	//todo 査看 DispatcherServlet.properties 中对应于 HandlerAdapter 的属性：
	//todo org.Springframework.web.servlet.HandlerAdapter-org
	//todo ・Springframework.web・servlet.mvc.Htt pRequestHandlerAdapterr\
	//todo org•Springframework.web.servlet.mvc.SimpleControllerHandlerAdapterz\
	//todo org.Springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter
	//todo 如果程序开发人员没有在配置文件中定义自己的适配器，那么Spring会默认加 载配置文件中的3个适配器

	static {
		// Load default strategy implementations from properties file.
		// This is currently strictly internal and not meant to be customized
		// by application developers.
		try {
			//todo 加载 DispatcherServlet.properties配置文件
			ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH, DispatcherServlet.class);
			//todo 加载默认策略（包括处理器适配器、处理器映射器、视图解析器这三大组件的默认策略）
			defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not load '" + DEFAULT_STRATEGIES_PATH + "': " + ex.getMessage());
		}
	}

	/** Detect all HandlerMappings or just expect "handlerMapping" bean? */
	private boolean detectAllHandlerMappings = true;

	/** Detect all HandlerAdapters or just expect "handlerAdapter" bean? */
	private boolean detectAllHandlerAdapters = true;

	/** Detect all HandlerExceptionResolvers or just expect "handlerExceptionResolver" bean? */
	private boolean detectAllHandlerExceptionResolvers = true;

	/** Detect all ViewResolvers or just expect "viewResolver" bean? */
	private boolean detectAllViewResolvers = true;

	/** Throw a NoHandlerFoundException if no Handler was found to process this request? **/
	private boolean throwExceptionIfNoHandlerFound = false;

	/** Perform cleanup of request attributes after include request? */
	private boolean cleanupAfterInclude = true;

	/** MultipartResolver used by this servlet */
	@Nullable
	private MultipartResolver multipartResolver;

	/** LocaleResolver used by this servlet */
	@Nullable
	private LocaleResolver localeResolver;

	/** ThemeResolver used by this servlet */
	@Nullable
	private ThemeResolver themeResolver;

	/** List of HandlerMappings used by this servlet */
	@Nullable
	private List<HandlerMapping> handlerMappings;

	/** List of HandlerAdapters used by this servlet */
	@Nullable
	private List<HandlerAdapter> handlerAdapters;

	/** List of HandlerExceptionResolvers used by this servlet */
	@Nullable
	private List<HandlerExceptionResolver> handlerExceptionResolvers;

	/** RequestToViewNameTranslator used by this servlet */
	@Nullable
	private RequestToViewNameTranslator viewNameTranslator;

	/** FlashMapManager used by this servlet */
	@Nullable
	private FlashMapManager flashMapManager;

	/** List of ViewResolvers used by this servlet */
	@Nullable
	private List<ViewResolver> viewResolvers;


	/**
	 * Create a new {@code DispatcherServlet} that will create its own internal web
	 * application context based on defaults and values provided through servlet
	 * init-params. Typically used in Servlet 2.5 or earlier environments, where the only
	 * option for servlet registration is through {@code web.xml} which requires the use
	 * of a no-arg constructor.
	 * <p>Calling {@link #setContextConfigLocation} (init-param 'contextConfigLocation')
	 * will dictate which XML files will be loaded by the
	 * {@linkplain #DEFAULT_CONTEXT_CLASS default XmlWebApplicationContext}
	 * <p>Calling {@link #setContextClass} (init-param 'contextClass') overrides the
	 * default {@code XmlWebApplicationContext} and allows for specifying an alternative class,
	 * such as {@code AnnotationConfigWebApplicationContext}.
	 * <p>Calling {@link #setContextInitializerClasses} (init-param 'contextInitializerClasses')
	 * indicates which {@code ApplicationContextInitializer} classes should be used to
	 * further configure the internal application context prior to refresh().
	 * @see #DispatcherServlet(WebApplicationContext)
	 */
	public DispatcherServlet() {
		super();
		setDispatchOptionsRequest(true);
	}

	/**
	 * Create a new {@code DispatcherServlet} with the given web application context. This
	 * constructor is useful in Servlet 3.0+ environments where instance-based registration
	 * of servlets is possible through the {@link ServletContext#addServlet} API.
	 * <p>Using this constructor indicates that the following properties / init-params
	 * will be ignored:
	 * <ul>
	 * <li>{@link #setContextClass(Class)} / 'contextClass'</li>
	 * <li>{@link #setContextConfigLocation(String)} / 'contextConfigLocation'</li>
	 * <li>{@link #setContextAttribute(String)} / 'contextAttribute'</li>
	 * <li>{@link #setNamespace(String)} / 'namespace'</li>
	 * </ul>
	 * <p>The given web application context may or may not yet be {@linkplain
	 * ConfigurableApplicationContext#refresh() refreshed}. If it has <strong>not</strong>
	 * already been refreshed (the recommended approach), then the following will occur:
	 * <ul>
	 * <li>If the given context does not already have a {@linkplain
	 * ConfigurableApplicationContext#setParent parent}, the root application context
	 * will be set as the parent.</li>
	 * <li>If the given context has not already been assigned an {@linkplain
	 * ConfigurableApplicationContext#setId id}, one will be assigned to it</li>
	 * <li>{@code ServletContext} and {@code ServletConfig} objects will be delegated to
	 * the application context</li>
	 * <li>{@link #postProcessWebApplicationContext} will be called</li>
	 * <li>Any {@code ApplicationContextInitializer}s specified through the
	 * "contextInitializerClasses" init-param or through the {@link
	 * #setContextInitializers} property will be applied.</li>
	 * <li>{@link ConfigurableApplicationContext#refresh refresh()} will be called if the
	 * context implements {@link ConfigurableApplicationContext}</li>
	 * </ul>
	 * If the context has already been refreshed, none of the above will occur, under the
	 * assumption that the user has performed these actions (or not) per their specific
	 * needs.
	 * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
	 * @param webApplicationContext the context to use
	 * @see #initWebApplicationContext
	 * @see #configureAndRefreshWebApplicationContext
	 * @see org.springframework.web.WebApplicationInitializer
	 */
	public DispatcherServlet(WebApplicationContext webApplicationContext) {
		super(webApplicationContext);
		setDispatchOptionsRequest(true);
	}


	/**
	 * Set whether to detect all HandlerMapping beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerMapping" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerMapping, despite multiple HandlerMapping beans being defined in the context.
	 */
	public void setDetectAllHandlerMappings(boolean detectAllHandlerMappings) {
		this.detectAllHandlerMappings = detectAllHandlerMappings;
	}

	/**
	 * Set whether to detect all HandlerAdapter beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerAdapter" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerAdapter, despite multiple HandlerAdapter beans being defined in the context.
	 */
	public void setDetectAllHandlerAdapters(boolean detectAllHandlerAdapters) {
		this.detectAllHandlerAdapters = detectAllHandlerAdapters;
	}

	/**
	 * Set whether to detect all HandlerExceptionResolver beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerExceptionResolver" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerExceptionResolver, despite multiple HandlerExceptionResolver beans being defined in the context.
	 */
	public void setDetectAllHandlerExceptionResolvers(boolean detectAllHandlerExceptionResolvers) {
		this.detectAllHandlerExceptionResolvers = detectAllHandlerExceptionResolvers;
	}

	/**
	 * Set whether to detect all ViewResolver beans in this servlet's context. Otherwise,
	 * just a single bean with name "viewResolver" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * ViewResolver, despite multiple ViewResolver beans being defined in the context.
	 */
	public void setDetectAllViewResolvers(boolean detectAllViewResolvers) {
		this.detectAllViewResolvers = detectAllViewResolvers;
	}

	/**
	 * Set whether to throw a NoHandlerFoundException when no Handler was found for this request.
	 * This exception can then be caught with a HandlerExceptionResolver or an
	 * {@code @ExceptionHandler} controller method.
	 * <p>Note that if {@link org.springframework.web.servlet.resource.DefaultServletHttpRequestHandler}
	 * is used, then requests will always be forwarded to the default servlet and a
	 * NoHandlerFoundException would never be thrown in that case.
	 * <p>Default is "false", meaning the DispatcherServlet sends a NOT_FOUND error through the
	 * Servlet response.
	 * @since 4.0
	 */
	public void setThrowExceptionIfNoHandlerFound(boolean throwExceptionIfNoHandlerFound) {
		this.throwExceptionIfNoHandlerFound = throwExceptionIfNoHandlerFound;
	}

	/**
	 * Set whether to perform cleanup of request attributes after an include request, that is,
	 * whether to reset the original state of all request attributes after the DispatcherServlet
	 * has processed within an include request. Otherwise, just the DispatcherServlet's own
	 * request attributes will be reset, but not model attributes for JSPs or special attributes
	 * set by views (for example, JSTL's).
	 * <p>Default is "true", which is strongly recommended. Views should not rely on request attributes
	 * having been set by (dynamic) includes. This allows JSP views rendered by an included controller
	 * to use any model attributes, even with the same names as in the main JSP, without causing side
	 * effects. Only turn this off for special needs, for example to deliberately allow main JSPs to
	 * access attributes from JSP views rendered by an included controller.
	 */
	public void setCleanupAfterInclude(boolean cleanupAfterInclude) {
		this.cleanupAfterInclude = cleanupAfterInclude;
	}


	/**
	 * This implementation calls {@link #initStrategies}.
	 */
	@Override
	protected void onRefresh(ApplicationContext context) {
		//todo 初始化策略容器
		initStrategies(context);
	}

	/**
	 * 初始化策略对象
	 * 
	 * Initialize the strategy objects that this servlet uses.
	 * <p>May be overridden in subclasses in order to initialize further strategy objects.
	 */
	protected void initStrategies(ApplicationContext context) {
		//todo 初始化多部件解析器
		initMultipartResolver(context);
		//todo 初始化国际化解析器
		initLocaleResolver(context);
		//todo 初始化主题解析器
		initThemeResolver(context);
		//todo 初始化处理器映射器
		initHandlerMappings(context);
		//todo 初始化处理器适配器
		initHandlerAdapters(context);
		//todo 初始化异常解析器
		initHandlerExceptionResolvers(context);
		initRequestToViewNameTranslator(context);
		//todo 初始会视图解析器
		initViewResolvers(context);
		initFlashMapManager(context);
	}

	/**
	 * Initialize the MultipartResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * no multipart handling is provided.
	 * todo 在Spring中.MultipartResolver主要用来处理文件上传。默认情况R Spring是没有multipart 处理的，
	 * todo 因为一些开发者想要自己处理它们。如果想使用Spring的multipart,则需要在Web 应用的上下文中添加multipan解析器°这样
	 * todo ，每个请求就会被检査是否包含multipart.然而， 如果清求中包含multipart,那么上下文中定义的MultipartResolver
	 * todo 就会解析它，这样请求中的 multipart属性就会象其他属性一样被处理。常用配置如下：
	 <bean id=“multipartResolver” class=“org.Springframework.web.multipart.commons.CommonsMultipartResolver”>
	 <!・・该属性用来配置可上传文件的最大byte数・・>
	 <property name=rtmaximuTnFileSizew><value>100000</value></property>
	 </bean>
	 todo CommonsMultipartResolver还提供了其他功能用于帮助用户完成上传功能
	 todo MuItipartResolver 就是在 initMultipartResolver 中被加入到 DispatcherServlet
	 */
	private void initMultipartResolver(ApplicationContext context) {
		try {
			//todo 因为之前的步骤已经完成了Spring中配置文件的解析.所以在这里只要在配置文件注册过都可以通过Applicationcontext
			//todo 提供的getBean方法来直接获取对应bean,进而初始化MuItipartResolver中的 multipartResolver变量
			this.multipartResolver = context.getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using MultipartResolver [" + this.multipartResolver + "]");
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// Default is no multipart resolver.
			this.multipartResolver = null;
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate MultipartResolver with name '" + MULTIPART_RESOLVER_BEAN_NAME +
						"': no multipart request handling provided");
			}
		}
	}

	/**
	 * Initialize the LocaleResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to AcceptHeaderLocaleResolver.
	 * todo 初始化 LocaleResolver
	 * todo 在Spring的国际化配置中一共有3种使用方式
	 * todo 1基于URL参数的配置
	 * 通过URL参数来控制国际化，比如你在页面上加一句<a href=”?Iocale=zh_CN”>简体中文 </a>来控制项目中使用的国际化参数，
	 * 而提供这个功能的就是AcceptHeaderLocaleResolver,默 认的参数名为locale,注意大小写。里面放的就是你的提交参数，
	 * 比如en_US. zh_CN之类的， 具体配置如下；
	 <bean id="localeResolvert", class="org.Springframework.web.servlet.il8n.AcceptHeaderLocaleResolver"/>
	 基于session的配置
	 它通过检验用户会话中预置的属性来解析区域，最常用的是根据用户本次会话过程中的语言设定决定语言种类(例如，用户登录时选择语言种类，
	 则此次登录周期内统一使用此语言设 定)，如果该会话属性不存在，它会根据accept-IanguageHTTP头部确定默认区域
	 <bean id="localeResolver" class="org.Springframework.web.servlet.il8n.SessionLocaleResolver"/>
	 基于Cookie的国际化配置
	 CookieLocaleResolver用于通过浏览器的cookie设置取得Locale对象这种策略在应用程 序不支持会话或者状态必须保存在客户端时有用
	 <bean id="localeResolver" class="org.Springframework.web.servlet.il8n.CookieLocaLeResolver"/>
	 对于LocalResolver的使用基础是在 DispatcherServlet 中的初始化
	 *
	 *
	 */
	private void initLocaleResolver(ApplicationContext context) {
		try {
			this.localeResolver = context.getBean(LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using LocaleResolver [" + this.localeResolver + "]");
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.localeResolver = getDefaultStrategy(context, LocaleResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate LocaleResolver with name '" + LOCALE_RESOLVER_BEAN_NAME +
						"': using default [" + this.localeResolver + "]");
			}
		}
	}

	/**
	 * Initialize the ThemeResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to a FixedThemeResolver.
	 * todo 初始化 ThemeResolver
	 * 在Web开发中经常会遇到通过主题Theme来控制网页风格，这将进一步改善用户体验。简单地说，一个主题就是一组静态资源(比如样式表和图片)
	 *，它们可以影响应用程序的视觉效果。Spring中的主题功能和国际化功能非常类似。构成Spring主题功能
	 * todo 主题资源
	 * org.Springframework.ui.context.ThemeSource是Spring中主题资源的接口 ‘ Spring的主题要通过ThemeSource接口来实现存放主题信息的资源
	 * org.Springframework.ui.context.support.ResourceBundleThemeSource是ThemeSource接口默认实现类(也就是通过ResourceBundle资源的方式定义主题)
	 * ，在Spring中的配置如下：
	 <bean id=“themeSource” class=“org.Springframework.ui.context.support.ResourceBundle.ThemeSource">
	 <property name="basenamePrefix" value="com.test. "></property>
	 </bean>
	 默认状态下是在类路径根目录下査找相应的资源文件，也可以通过basenamePrefix来制定
	 DispatcherServlet血就会在com.test包下查找资源文件
	 todo 主题解析器
	 org.Springframework.web.servlet.ThemeResolver是主题解析器的接口，主题解析的工作便是由它的子类来完成
	 对于主题解析器的子类主要有3个比较常用的实现。以主题文件summer.propenies为例
	 todo FixedThemeResolver用于选择一个固定的主题。
	 <bean	class="org.Springframework.web.servlet.theme.FixedThemeResolver">
	 <property name="defau1tThemeName" value="summer'/>
	 </bean>
	 以上配置的作用是设置主题文件为summer.properties,在整个项目内固定不变
	 todo CookieThemeResolver用于实现用户所选的主题，以cookie的形式存放在客户端的机器上，配置如下
	 <bean id="themeResolver" class="org.Springframework.web.servlet.theme.CookieThemeResolver">
	  <property name="defaultThemeName" value="summer"/>
	 </Dean>
	 todo SessionThemeResolver用于主题保存在用户的HTTP Session中
	 <bean id="themeResolver" class="org.Springframework.web.servlet.theme.SessionThemeResolver">
	 <property name="defaultThemeName" value="summer“/>
	 </bean>
	 以上配置用于设置主题名称，并且将该名称保存在用户的HttpSession中
	 todo AbstractThemeResolver是一个抽象类被SessionThemeResolver和FixedThemeResolver继承,用户也可以继承它来自定义主题解析器
	 todo 拦截器
	 todo 如果需要根据用户请求来改变主题.那么Spring提供了一个已经实现的拦截器ThemeChangeInterceptor拦截器了，配置如下
	 <bean id="themechangeInterceptor" class="org.Springframework.web.servlet.theme.ThemeChangeInterceptor">
	 	<property name="paramName" value="themeName"></property>
	 </bean>
	 其中设置用户请求参数名为themeName，即URL为?themeNam=具体的主题名称。此外, 还需要在handlerMapping中配置拦截器
	 。当然需要在HandleMapping中添加拦截器
	 <property name="interceptors">
	 	<list>
	 		<ref local="themeChangeInterceptor" />
	 	</list>
	 </property>
	 */
	private void initThemeResolver(ApplicationContext context) {
		try {
			this.themeResolver = context.getBean(THEME_RESOLVER_BEAN_NAME, ThemeResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using ThemeResolver [" + this.themeResolver + "]");
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.themeResolver = getDefaultStrategy(context, ThemeResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate ThemeResolver with name '" + THEME_RESOLVER_BEAN_NAME +
						"': using default [" + this.themeResolver + "]");
			}
		}
	}

	/**
	 * Initialize the HandlerMappings used by this class.
	 * <p>If no HandlerMapping beans are defined in the BeanFactory for this namespace,
	 * we default to BeanNameUrlHandlerMapping.
	 * todo 初始化HandlerMapping
	 * SpringMVC将加载当前系统中所有实现了 HandlerMapping接口的bean。如 果只期聖SpringMVC加载指定的handlermapping时，可以修改web.xml中的DispatcherServlet 的初始参数，将detectAllHandlerMappings的值设置为false：
	 <init-param>
	 <param-name>detectAllHandlerMappings</param-name>
	 <param-value>false</param-value>
	 </init-param>
	 此时，SpringMVC将查找名 为"handlerMapping"的bean,并作为当前系统中唯一的 handlermapping.
	 如果没有定义 HandlerMapping 的话，则 SpringMVC 将按照 org.Springframework.web.servlet.DispatcherServlet
	 所在目录下的DispatcherServlet.properties中所定义的 org.Springframework.web.servlet.HandlerMapping
	 的内容来加载默认的 HandlerMapping(用户没有自定义Strategies的情况下)
	 */
	private void initHandlerMappings(ApplicationContext context) {
		this.handlerMappings = null;

		if (this.detectAllHandlerMappings) {
			// Find all HandlerMappings in the ApplicationContext, including ancestor contexts.
			//todo 从当前spring容器和其祖先容器中，查找到所有的处理器映射器
			Map<String, HandlerMapping> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerMappings = new ArrayList<>(matchingBeans.values());
				// We keep HandlerMappings in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerMappings);
			}
		}
		else {
			try {
				HandlerMapping hm = context.getBean(HANDLER_MAPPING_BEAN_NAME, HandlerMapping.class);
				this.handlerMappings = Collections.singletonList(hm);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerMapping later.
			}
		}

		// Ensure we have at least one HandlerMapping, by registering
		// a default HandlerMapping if no other mappings are found.
		if (this.handlerMappings == null) {
			//todo 如果没有从spring容器中获取到HandlerMapping，则从默认DispatcherServlet.properties文件中加载默认策略
			this.handlerMappings = getDefaultStrategies(context, HandlerMapping.class);
			if (logger.isDebugEnabled()) {
				logger.debug("No HandlerMappings found in servlet '" + getServletName() + "': using default");
			}
		}
	}

	/**
	 * Initialize the HandlerAdapters used by this class.
	 * <p>If no HandlerAdapter beans are defined in the BeanFactory for this namespace,
	 * we default to SimpleControllerHandlerAdapter.
	 * todo 初始化 HandlerAdapters
	 *
	 * todo 作为总控制器的派遣器servlet通过处理器映射得到处理器后，会轮询处理器适配器模块， 查找能够处理当前HTTP请求的处理器适配器
	 * todo 的实现.处理器适配器模块根据处理器映射返回 的处理器类型，例如简单的控制器类型、注解控制器类型或者远程调用处理器类型，
	 * todo 来选择某 一个适当的处理器适配器的实现，从而适配当前的HTTP请求
	 */
	private void initHandlerAdapters(ApplicationContext context) {
		this.handlerAdapters = null;

		if (this.detectAllHandlerAdapters) {
			// Find all HandlerAdapters in the ApplicationContext, including ancestor contexts.
			//todo 从当前spring容器和其祖先容器中，查找到所有的处理器适配器
			Map<String, HandlerAdapter> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerAdapter.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerAdapters = new ArrayList<>(matchingBeans.values());
				// We keep HandlerAdapters in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerAdapters);
			}
		}
		else {
			try {
				HandlerAdapter ha = context.getBean(HANDLER_ADAPTER_BEAN_NAME, HandlerAdapter.class);
				this.handlerAdapters = Collections.singletonList(ha);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerAdapter later.
			}
		}
		//todo 同样在初始化的过程中涉及了一个变量detectA11HandlerAdapters,detectAllHandlerAdapters作用和detectAHHandlerMappings类似
		//todo ，只不过作用对象为handlerAdapter。亦可通过如下配置来强制系统只加载bean name为handlerAdapter" handlerAdapter.
		//todo <init-param>
		//todo <param-name>detectAlLHandlerAdapters</param-name>
		//todo <param-value>false</param-value>
		//todo </init-param>
		// Ensure we have at least some HandlerAdapters, by registering
		// default HandlerAdapters if no other adapters are found.
		if (this.handlerAdapters == null) {
			//todo 如果没有从spring容器中获取到HandlerAdapter，则从默认DispatcherServlet.properties文件中加载默认策略
			this.handlerAdapters = getDefaultStrategies(context, HandlerAdapter.class);
			if (logger.isDebugEnabled()) {
				logger.debug("No HandlerAdapters found in servlet '" + getServletName() + "': using default");
			}
		}
	}

	/**
	 * Initialize the HandlerExceptionResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to no exception resolver.
	 * todo 初始化 HandlerExceptionResolvers
	 * todo 基于HandlerExceptionResolver接口的异常处理，使用这种方式只需要实现resolveException方法,该方法返回一个
	 * todo ModelAndView对象，在方法内部对异常的类型进行判断，然后尝试生成对应的ModelAndView对象，如果该方法返回了 null,
	 * todo 则Spring会继续寻找其他的实现K HandlerExceptionResolver接口的bean,换句话说，Spring会搜索所有注册在其环境中的实现
	 * todo ( HandlerExceptionResolver接口的 bean,逐个执行，直到返回了一个 ModelAndView 对象
	 * <bean id="exceptionHandler" class="com.test.exception.MyExceptionHandler"/>
	 */
	private void initHandlerExceptionResolvers(ApplicationContext context) {
		this.handlerExceptionResolvers = null;

		if (this.detectAllHandlerExceptionResolvers) {
			// Find all HandlerExceptionResolvers in the ApplicationContext, including ancestor contexts.
			Map<String, HandlerExceptionResolver> matchingBeans = BeanFactoryUtils
					.beansOfTypeIncludingAncestors(context, HandlerExceptionResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerExceptionResolvers = new ArrayList<>(matchingBeans.values());
				// We keep HandlerExceptionResolvers in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerExceptionResolvers);
			}
		}
		else {
			try {
				HandlerExceptionResolver her =
						context.getBean(HANDLER_EXCEPTION_RESOLVER_BEAN_NAME, HandlerExceptionResolver.class);
				this.handlerExceptionResolvers = Collections.singletonList(her);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, no HandlerExceptionResolver is fine too.
			}
		}

		// Ensure we have at least some HandlerExceptionResolvers, by registering
		// default HandlerExceptionResolvers if no other resolvers are found.
		if (this.handlerExceptionResolvers == null) {
			this.handlerExceptionResolvers = getDefaultStrategies(context, HandlerExceptionResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("No HandlerExceptionResolvers found in servlet '" + getServletName() + "': using default");
			}
		}
	}

	/**
	 * Initialize the RequestToViewNameTranslator used by this servlet instance.
	 * <p>If no implementation is configured then we default to DefaultRequestToViewNameTranslator.
	 * todo 初始化 RequestToViewNameTranslator
	 * todo 当Controller处理器方法没有返回一个View对象或逻辑视图名称，并且在该方法中没有直接往response的输出流里面写数据的时候
	 * todo ，Spring就会采用约定好的方式提供一个逻辑视图名 称。这个逻辑视图名称是通过 Spring 定义的
	 * todo org.Springfi"amework.web.servlet.RequestToView NameTranslator接口的getViewName方法来实现的，
	 * todo 我们可以实现自己的RequestToViewNameTranslator接口来约定好没有返回视图名称的时候如何确定视图名称Spring已经给我们提供了
	 * todo 一个它自己的实现，那就是 org.Springframework.web.servlet.view.DefaultRcquestToViewNameTransiator
	 * todo 在介绍DefaultRequestToViewNameTranslator是如何约定视图名称之前，先来看一下它支 持用户定义的属性
	 * todo prefix:前缀，表示约定好的视图名称需要加上的前缀，默认是空串
	 * todo suffix:后缀，表示约定好的视图名称需要加上的后缀，默认是空串
	 * todo separator：分隔符，默认是斜杠
	 * todo stripLeadingSlash:如果首字符是分隔符，是否要去除，默认是true
	 * todo stripTrailingSlash:如果最后一个字符是分隔符，是否要去除，默认是true
	 * todo stripExtension:如果请求路径包含扩展名是否要去除，默认是true.
	 * todo urlDecode：是否需要对URL解码，默认是true.它会釆用request指定的编码或者 ISO-8859-1编玛对URL进行解码
	 *todo  当我们没有在SpringMVC的配置文件中手动的定义一个名为viewNameTranlator的Bean的时候,Spring就会为我们提供一个默认的
	 *todo viewNameTranslator,即 DefaultRequestToViewNameTranslator
	 *
	 */
	private void initRequestToViewNameTranslator(ApplicationContext context) {
		try {
			this.viewNameTranslator =
					context.getBean(REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME, RequestToViewNameTranslator.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using RequestToViewNameTranslator [" + this.viewNameTranslator + "]");
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.viewNameTranslator = getDefaultStrategy(context, RequestToViewNameTranslator.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate RequestToViewNameTranslator with name '" +
						REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME + "': using default [" + this.viewNameTranslator +
						"]");
			}
		}
	}

	/**
	 * Initialize the ViewResolvers used by this class.
	 * <p>If no ViewResolver beans are defined in the BeanFactory for this
	 * namespace, we default to InternalResourceViewResolver.
	 * todo 初始化视图解析器
	 * todo 在SpringMVC中，当Controller将请求处理结果放入到 ModelAndView中以后,Dispatcherservlet会根据ModelAndView
	 * todo 选择合适的视图进行渲染。那么在SpringMVC中是如何选择合适的View呢？
	 * todo view对象是是如何创建的呢？答案就在ViewResolver中° ViewResolver 接口定义了 resolverViewName方法，
	 * todo 根据viewName创建合适类型的View实现
	 * todo 那么如何配置ViewResolver呢？在Spring中，ViewResolver作为Spring Bean存在，可以在Spring配置文件中进行配置，
	 * todo 例如下面的代码，配置了 JSP相关的ViewResolver。
	 <bean class="org.Springframework•web.servlet・view.internalResourceViewResolver">
	   <property name="prefix" value="/WEB-INF/views/"/>
	 <property name="suffix" value=".jsp"/>
	 </bean>
	 */
	private void initViewResolvers(ApplicationContext context) {
		this.viewResolvers = null;

		if (this.detectAllViewResolvers) {
			// Find all ViewResolvers in the ApplicationContext, including ancestor contexts.
			//todo 从当前spring容器和其祖先容器中，查找到所有的视图解析器
			Map<String, ViewResolver> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, ViewResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.viewResolvers = new ArrayList<>(matchingBeans.values());
				// We keep ViewResolvers in sorted order.
				AnnotationAwareOrderComparator.sort(this.viewResolvers);
			}
		}
		else {
			try {
				ViewResolver vr = context.getBean(VIEW_RESOLVER_BEAN_NAME, ViewResolver.class);
				this.viewResolvers = Collections.singletonList(vr);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default ViewResolver later.
			}
		}

		// Ensure we have at least one ViewResolver, by registering
		// a default ViewResolver if no other resolvers are found.
		if (this.viewResolvers == null) {
			//todo 如果没有从spring容器中获取到ViewResolver，则从默认DispatcherServlet.properties文件中加载默认策略
			this.viewResolvers = getDefaultStrategies(context, ViewResolver.class);
			if (logger.isDebugEnabled()) {
				logger.debug("No ViewResolvers found in servlet '" + getServletName() + "': using default");
			}
		}
	}

	/**
	 * Initialize the {@link FlashMapManager} used by this servlet instance.
	 * <p>If no implementation is configured then we default to
	 * {@code org.springframework.web.servlet.support.DefaultFlashMapManager}.
	 * todo 初始化FlashMapManager
	 * todo SpringMVC Flashattributes提供了一个请求存储属性，可供其他请求使用。在使用重定向时候非常必要，
	 * todo 例如Post/Redirect/Get模式。Flash attributes在重定向之前暂存(就像存在session 中)以便重定向之后还能使用，并立即删除
	 * todo SpringMVC 有两个主要的抽象来支持 flash attributes FlashMap 用于保持 flash attributes ,
	 * todo 而FlashMapManager用于存储、检索、管理FlashMap实例
	 * todo flash attribute支持默认开启(”on”)并不需要显式启用，它永远不会导致HTTPSession的 创建。
	 * todo 这两个FlashMap实例都可以通过静态方法RequestContextUtils从Spring MVC的任何位 置访问
	 */
	private void initFlashMapManager(ApplicationContext context) {
		try {
			this.flashMapManager = context.getBean(FLASH_MAP_MANAGER_BEAN_NAME, FlashMapManager.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using FlashMapManager [" + this.flashMapManager + "]");
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.flashMapManager = getDefaultStrategy(context, FlashMapManager.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate FlashMapManager with name '" +
						FLASH_MAP_MANAGER_BEAN_NAME + "': using default [" + this.flashMapManager + "]");
			}
		}
	}

	/**
	 * Return this servlet's ThemeSource, if any; else return {@code null}.
	 * <p>Default is to return the WebApplicationContext as ThemeSource,
	 * provided that it implements the ThemeSource interface.
	 * @return the ThemeSource, if any
	 * @see #getWebApplicationContext()
	 */
	@Nullable
	public final ThemeSource getThemeSource() {
		return (getWebApplicationContext() instanceof ThemeSource ? (ThemeSource) getWebApplicationContext() : null);
	}

	/**
	 * Obtain this servlet's MultipartResolver, if any.
	 * @return the MultipartResolver used by this servlet, or {@code null} if none
	 * (indicating that no multipart support is available)
	 */
	@Nullable
	public final MultipartResolver getMultipartResolver() {
		return this.multipartResolver;
	}

	/**
	 * Return the configured {@link HandlerMapping} beans that were detected by
	 * type in the {@link WebApplicationContext} or initialized based on the
	 * default set of strategies from {@literal DispatcherServlet.properties}.
	 * <p><strong>Note:</strong> This method may return {@code null} if invoked
	 * prior to {@link #onRefresh(ApplicationContext)}.
	 * @return an immutable list with the configured mappings, or {@code null}
	 * if not initialized yet
	 * @since 5.0
	 */
	@Nullable
	public final List<HandlerMapping> getHandlerMappings() {
		return (this.handlerMappings != null ? Collections.unmodifiableList(this.handlerMappings) : null);
	}

	/**
	 * Return the default strategy object for the given strategy interface.
	 * <p>The default implementation delegates to {@link #getDefaultStrategies},
	 * expecting a single object in the list.
	 * @param context the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the corresponding strategy object
	 * @see #getDefaultStrategies
	 */
	protected <T> T getDefaultStrategy(ApplicationContext context, Class<T> strategyInterface) {
		List<T> strategies = getDefaultStrategies(context, strategyInterface);
		if (strategies.size() != 1) {
			throw new BeanInitializationException(
					"DispatcherServlet needs exactly 1 strategy for interface [" + strategyInterface.getName() + "]");
		}
		return strategies.get(0);
	}

	/**
	 * Create a List of default strategy objects for the given strategy interface.
	 * <p>The default implementation uses the "DispatcherServlet.properties" file (in the same
	 * package as the DispatcherServlet class) to determine the class names. It instantiates
	 * the strategy objects through the context's BeanFactory.
	 * @param context the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the List of corresponding strategy objects
	 * todo 获取默认的适配器
	 */
	@SuppressWarnings("unchecked")
	protected <T> List<T> getDefaultStrategies(ApplicationContext context, Class<T> strategyInterface) {
		//todo 获取策略接口的名称
		String key = strategyInterface.getName();
		//todo 获取具体策略类的名称 spring尝试从defaultStrategies中加载对应的HandlerAdapter的属性,
		//todo 那么defaultStrategies是如何初始化的呢
		String value = defaultStrategies.getProperty(key);
		if (value != null) {
			//todo 获取所有具体策略类的名称集合
			String[] classNames = StringUtils.commaDelimitedListToStringArray(value);
			List<T> strategies = new ArrayList<>(classNames.length);
			for (String className : classNames) {
				try {
					Class<?> clazz = ClassUtils.forName(className, DispatcherServlet.class.getClassLoader());
					//todo 创建具体的策略实例
					Object strategy = createDefaultStrategy(context, clazz);
					strategies.add((T) strategy);
				}
				catch (ClassNotFoundException ex) {
					throw new BeanInitializationException(
							"Could not find DispatcherServlet's default strategy class [" + className +
							"] for interface [" + key + "]", ex);
				}
				catch (LinkageError err) {
					throw new BeanInitializationException(
							"Unresolvable class definition for DispatcherServlet's default strategy class [" +
							className + "] for interface [" + key + "]", err);
				}
			}
			return strategies;
		}
		else {
			return new LinkedList<>();
		}
	}

	/**
	 * Create a default strategy.
	 * <p>The default implementation uses
	 * {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean}.
	 * @param context the current WebApplicationContext
	 * @param clazz the strategy implementation class to instantiate
	 * @return the fully configured strategy instance
	 * @see org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()
	 * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean
	 */
	protected Object createDefaultStrategy(ApplicationContext context, Class<?> clazz) {
		return context.getAutowireCapableBeanFactory().createBean(clazz);
	}


	/**
	 * Exposes the DispatcherServlet-specific request attributes and delegates to {@link #doDispatch}
	 * for the actual dispatching.
	 * todo 在doDispatch中实现主要业务
	 */
	@Override
	protected void doService(HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (logger.isDebugEnabled()) {
			String resumed = WebAsyncUtils.getAsyncManager(request).hasConcurrentResult() ? " resumed" : "";
			logger.debug("DispatcherServlet with name '" + getServletName() + "'" + resumed +
					" processing " + request.getMethod() + " request for [" + getRequestUri(request) + "]");
		}

		// Keep a snapshot of the request attributes in case of an include,
		// to be able to restore the original attributes after the include.
		Map<String, Object> attributesSnapshot = null;
		if (WebUtils.isIncludeRequest(request)) {
			attributesSnapshot = new HashMap<>();
			Enumeration<?> attrNames = request.getAttributeNames();
			while (attrNames.hasMoreElements()) {
				String attrName = (String) attrNames.nextElement();
				if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
					attributesSnapshot.put(attrName, request.getAttribute(attrName));
				}
			}
		}

		// Make framework objects available to handlers and view objects.
		request.setAttribute(WEB_APPLICATION_CONTEXT_ATTRIBUTE, getWebApplicationContext());
		request.setAttribute(LOCALE_RESOLVER_ATTRIBUTE, this.localeResolver);
		request.setAttribute(THEME_RESOLVER_ATTRIBUTE, this.themeResolver);
		request.setAttribute(THEME_SOURCE_ATTRIBUTE, getThemeSource());

		if (this.flashMapManager != null) {
			FlashMap inputFlashMap = this.flashMapManager.retrieveAndUpdate(request, response);
			if (inputFlashMap != null) {
				request.setAttribute(INPUT_FLASH_MAP_ATTRIBUTE, Collections.unmodifiableMap(inputFlashMap));
			}
			request.setAttribute(OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
			request.setAttribute(FLASH_MAP_MANAGER_ATTRIBUTE, this.flashMapManager);
		}

		try {
			//todo 处理请求分发（做调度）
			doDispatch(request, response);
		}
		finally {
			if (!WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
				// Restore the original attribute snapshot, in case of an include.
				if (attributesSnapshot != null) {
					restoreAttributesAfterInclude(request, attributesSnapshot);
				}
			}
		}
	}

	/**
	 * Process the actual dispatching to the handler.
	 * <p>The handler will be obtained by applying the servlet's HandlerMappings in order.
	 * The HandlerAdapter will be obtained by querying the servlet's installed HandlerAdapters
	 * to find the first that supports the handler class.
	 * <p>All HTTP methods are handled by this method. It's up to HandlerAdapters or handlers
	 * themselves to decide which methods are acceptable.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception in case of any kind of processing failure
	 */
	protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
		HttpServletRequest processedRequest = request;
		HandlerExecutionChain mappedHandler = null;
		boolean multipartRequestParsed = false;

		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);

		try {
			ModelAndView mv = null;
			Exception dispatchException = null;

			try {
				//todo 处理文件上传的request请求
				//todo 如果是 Multipartcontent类型的request则转换request为MultipartHttpServletRequest类型的request
				processedRequest = checkMultipart(request);
				multipartRequestParsed = (processedRequest != request);

				// Determine handler for the current request.
				//todo 通过处理器映射器HandlerMapping，获取handler处理器执行链，该执行链封装了处理器和对应该处理器的拦截器（可能有多个）
				//todo 需要注意的是@Controller注解的类，它不是我们这里要查找的处理器，
				//todo 我们要查找的处理器是@RequestMapping对应的方法，这个方法会封装到HandlerMethod类中
				//todo 根据request信息寻找对应的Handler
				mappedHandler = getHandler(processedRequest);
				if (mappedHandler == null) {
					//todo 如果没有找到对应的handler则通过response反馈错误信息
					noHandlerFound(processedRequest, response);
					return;
				}

				// Determine handler adapter for the current request.
				//todo 通过找到的handler处理器，去匹配合适的处理器适配器HandlerAdapter
				HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

				// Process last-modified header, if supported by the handler.
				//todo 如果当前handler支持last-modified头处理  Last-Modified缓存机制
				//todo 1 在客户端第一次输入URL时，服务器端会返回内容和状态码200,表示请求成功，同 时会添加一个“Last.Modified”
				// todo 的响应头，表示此文件在服务器上的最后更新时间，例如， "Last-Modified:Wed, 14 Mar 2012 10:22:42 GMT”
				// todo 表示最后更新时间为(2012-03-14 10:22 )
				//todo 2 客户端第二次请求此URL时，客户端会向服务器发送请求头"IJModified. Since”，询 问服务器该时间之后当前请求内容是
				//todo  否有被修改过，如••If-Modified-Since: Wed, 14 Mar 2012 10:22:42 GMT\如果服务器端的内容没有变化，则自动返冋
				//todo HTTP304状态码(只要响应头， 内容为空，这样就节省了网络带宽)
				//todo Spring提供的对Last-Modified机制的支持，只需要实现LastModified接口
				//todo Spring判断是否过期，通过判断清求的"If-Modified-Since"是否大于等于当前的getLast
				//todo Modified方法的时冋戳，如果是，则认为没有修改。上面的controller与普通的controller并无 太大差别，声明如下：
				//todo <bean name=“/helloLastModified” class=“com.test.controller.HelloWorIdLastModifiedCacheController”/>
				String method = request.getMethod();
				boolean isGet = "GET".equals(method);
				if (isGet || "HEAD".equals(method)) {
					long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
					if (logger.isDebugEnabled()) {
						logger.debug("Last-Modified value for [" + getRequestUri(request) + "] is: " + lastModified);
					}
					if (new ServletWebRequest(request, response).checkNotModified(lastModified) && isGet) {
						return;
					}
				}
				//todo SpringMVC允许你通过处理拦截Web请求，进行前置处理和后置处理。处理拦截是在 Spring的Web应用程序上下文中配置的
				//todo ，因此它们可以利用各种容器特性，并引用容器中声明 的任何bean3处理拦截是针对特殊的处理程序映射进行注册的，
				//todo 因此它只拦截通过这些处理程 序映射的请求。每个处理拦截都必须实现Handlerinterceptor接口，
				//todo 它包含三个需要你实现的回 调方法：preHandle()、postHandle()和afterCompletion().
				//todo 第一个和第二个方法分別是在处理程 序处理请求之前和之后被调用的。第二个方法还允许访问返回的ModclAndView对象，
				//todo 因此可 以在它里面操作模型属性。最后一个方法是在所有请求处理完成之后被调用的(如视图呈现之 后)
				//todo  执行拦截器(interceptor)的preHandle方法
				if (!mappedHandler.applyPreHandle(processedRequest, response)) {
					return;
				}

				// Actually invoke the handler.
				//todo  通过处理器适配器，真正调用处理器方法 真正的激活handler并返回视图
				//todo 对于普通的Web请求，Spring 默认使用 SimpleControllerHandlerAdapter类进行处理
				mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

				if (asyncManager.isConcurrentHandlingStarted()) {
					return;
				}
				//todo 设置默认视图名称
				applyDefaultViewName(processedRequest, mv);
				//todo 执行拦截器(interceptor)的postHandle方法
				mappedHandler.applyPostHandle(processedRequest, response, mv);
			}
			catch (Exception ex) {
				dispatchException = ex;
			}
			catch (Throwable err) {
				// As of 4.3, we're processing Errors thrown from handler methods as well,
				// making them available for @ExceptionHandler methods and other scenarios.
				dispatchException = new NestedServletException("Handler dispatch failed", err);
			}
			//todo 处理调度结果（也就是ModelAndView对象）
			processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
		}
		catch (Exception ex) {
			//todo 执行拦截器(interceptor)的afterCompletion方法 完成处理激活触发器
			triggerAfterCompletion(processedRequest, response, mappedHandler, ex);
		}
		catch (Throwable err) {
			//todo 执行拦截器(interceptor)的afterCompletion方法 完成处理激活触发器
			triggerAfterCompletion(processedRequest, response, mappedHandler,
					new NestedServletException("Handler processing failed", err));
		}
		finally {
			if (asyncManager.isConcurrentHandlingStarted()) {
				// Instead of postHandle and afterCompletion
				if (mappedHandler != null) {
					mappedHandler.applyAfterConcurrentHandlingStarted(processedRequest, response);
				}
			}
			else {
				// Clean up any resources used by a multipart request.
				if (multipartRequestParsed) {
					cleanupMultipart(processedRequest);
				}
			}
		}
	}

	/**
	 * Do we need view name translation?
	 */
	private void applyDefaultViewName(HttpServletRequest request, @Nullable ModelAndView mv) throws Exception {
		//todo 视图名称转换应用于需要添加前缀后綴的情况
		if (mv != null && !mv.hasView()) {
			String defaultViewName = getDefaultViewName(request);
			if (defaultViewName != null) {
				mv.setViewName(defaultViewName);
			}
		}
	}

	/**
	 * Handle the result of handler selection and handler invocation, which is
	 * either a ModelAndView or an Exception to be resolved to a ModelAndView.
	 */
	private void processDispatchResult(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerExecutionChain mappedHandler, @Nullable ModelAndView mv,
			@Nullable Exception exception) throws Exception {

		boolean errorView = false;
		
		//todo 处理异常
		if (exception != null) {
			if (exception instanceof ModelAndViewDefiningException) {
				logger.debug("ModelAndViewDefiningException encountered", exception);
				mv = ((ModelAndViewDefiningException) exception).getModelAndView();
			}
			else {
				Object handler = (mappedHandler != null ? mappedHandler.getHandler() : null);
				//todo 调用异常处理器，处理异常
				mv = processHandlerException(request, response, handler, exception);
				errorView = (mv != null);
			}
		}

		// Did the handler return a view to render?
		//todo 如果在Handler实例的处理中返回了view,那么需要做页面定的处理
		if (mv != null && !mv.wasCleared()) {
			//todo 通过视图解析器，解析视图对象之后，进行视图渲染 处理页面跳转
			render(mv, request, response);
			if (errorView) {
				WebUtils.clearErrorRequestAttributes(request);
			}
		}
		else {
			if (logger.isDebugEnabled()) {
				logger.debug("Null ModelAndView returned to DispatcherServlet with name '" + getServletName() +
						"': assuming HandlerAdapter completed request handling");
			}
		}

		if (WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
			// Concurrent handling started during a forward
			return;
		}

		//todo 触发处理器拦截器的afterCompletion方法
		if (mappedHandler != null) {
			mappedHandler.triggerAfterCompletion(request, response, null);
		}
	}

	/**
	 * Build a LocaleContext for the given request, exposing the request's primary locale as current locale.
	 * <p>The default implementation uses the dispatcher's LocaleResolver to obtain the current locale,
	 * which might change during a request.
	 * @param request current HTTP request
	 * @return the corresponding LocaleContext
	 */
	@Override
	protected LocaleContext buildLocaleContext(final HttpServletRequest request) {
		LocaleResolver lr = this.localeResolver;
		if (lr instanceof LocaleContextResolver) {
			return ((LocaleContextResolver) lr).resolveLocaleContext(request);
		}
		else {
			return () -> (lr != null ? lr.resolveLocale(request) : request.getLocale());
		}
	}

	/**
	 * Convert the request into a multipart request, and make multipart resolver available.
	 * <p>If no multipart resolver is set, simply use the existing request.
	 * @param request current HTTP request
	 * @return the processed request (multipart wrapper if necessary)
	 * @see MultipartResolver#resolveMultipart
	 * todo MultipartContext类型request处理
	 */
	protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
		if (this.multipartResolver != null && this.multipartResolver.isMultipart(request)) {
			if (WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class) != null) {
				logger.debug("Request is already a MultipartHttpServletRequest - if not in a forward, " +
						"this typically results from an additional MultipartFilter in web.xml");
			}
			else if (hasMultipartException(request) ) {
				logger.debug("Multipart resolution failed for current request before - " +
						"skipping re-resolution for undisturbed error rendering");
			}
			else {
				try {
					//todo 通过多部件解析器，解析多部件方式的请求（也就是文件上传请求）
					return this.multipartResolver.resolveMultipart(request);
				}
				catch (MultipartException ex) {
					if (request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) != null) {
						logger.debug("Multipart resolution failed for error dispatch", ex);
						// Keep processing error dispatch with regular request handle below
					}
					else {
						throw ex;
					}
				}
			}
		}
		// If not returned before: return original request.
		return request;
	}

	/**
	 * Check "javax.servlet.error.exception" attribute for a multipart exception.
	 */
	private boolean hasMultipartException(HttpServletRequest request) {
		Throwable error = (Throwable) request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE);
		while (error != null) {
			if (error instanceof MultipartException) {
				return true;
			}
			error = error.getCause();
		}
		return false;
	}

	/**
	 * Clean up any resources used by the given multipart request (if any).
	 * @param request current HTTP request
	 * @see MultipartResolver#cleanupMultipart
	 */
	protected void cleanupMultipart(HttpServletRequest request) {
		if (this.multipartResolver != null) {
			MultipartHttpServletRequest multipartRequest =
					WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
			if (multipartRequest != null) {
				this.multipartResolver.cleanupMultipart(multipartRequest);
			}
		}
	}

	/**
	 * Return the HandlerExecutionChain for this request.
	 * <p>Tries all handler mappings in order.
	 * @param request current HTTP request
	 * @return the HandlerExecutionChain, or {@code null} if no handler could be found
	 * <bean id=ftsimpleUrlMappingM
	class="org.Springframework.web.servlet.handler.SimpleUrlHandlerMapping">
	<property name="mappings">
	<props>
	<prop key="/userlist.htm">userController</prop>
	</props>
	</property>
	</bean>
	todo 在Spring加载的过程中，Spring会将类型为SimplcUrlHandlerMapping的实例加载到 this.handlerMappings中，
	todo 按照常理推断，根据request提取对应的Handler,无非就是提取当前实例中的 userController,但是 userController
	todo 为继承自 Abstractcontroller类型实例，与 HandlerExecutionchain并无任何关联，那么这一步是如何封装的呢
	 */
	@Nullable
	protected HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		if (this.handlerMappings != null) {
			//todo 遍历所有的处理器映射器
			for (HandlerMapping hm : this.handlerMappings) {
				if (logger.isTraceEnabled()) {
					logger.trace(
							"Testing handler map [" + hm + "] in DispatcherServlet with name '" + getServletName() + "'");
				}
				//todo 通过处理器映射器，查找具体的处理器执行链
				HandlerExecutionChain handler = hm.getHandler(request);
				if (handler != null) {
					return handler;
				}
			}
		}
		return null;
	}

	/**
	 * No handler found -> set appropriate HTTP response status.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception if preparing the response failed
	 * todo 没找到对应的Handler的错误处理
	 *  todo 每个请求都应该对应着一 Handler,因为每个请求都会在后台有相应的逻辑对应，而逻辑的实现就是在Handler中，
	 *  todo 所以一旦遇到没有找到Handler的情况(正常情况下如果没有URL 匹配的Handler,开发人员可以设置默认的Handler来处理请求，
	 *  todo 但是如果默认请求也未设置就 会出现Handler为空的情况),就只能通过response向用户返回错误信息
	 */
	protected void noHandlerFound(HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (pageNotFoundLogger.isWarnEnabled()) {
			pageNotFoundLogger.warn("No mapping found for HTTP request with URI [" + getRequestUri(request) +
					"] in DispatcherServlet with name '" + getServletName() + "'");
		}
		if (this.throwExceptionIfNoHandlerFound) {
			throw new NoHandlerFoundException(request.getMethod(), getRequestUri(request),
					new ServletServerHttpRequest(request).getHeaders());
		}
		else {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	/**
	 * Return the HandlerAdapter for this handler object.
	 * @param handler the handler object to find an adapter for
	 * @throws ServletException if no HandlerAdapter can be found for the handler. This is a fatal error.
	 * todo 根据当前Handler寻找对应的HandlerAdapter
	 */
	protected HandlerAdapter getHandlerAdapter(Object handler) throws ServletException {
		if (this.handlerAdapters != null) {
			for (HandlerAdapter ha : this.handlerAdapters) {
				if (logger.isTraceEnabled()) {
					logger.trace("Testing handler adapter [" + ha + "]");
				}
				//todo 通过适配器的适配功能，去适配处理器，如果适配成功，则直接将适配器返回
				if (ha.supports(handler)) {
					return ha;
				}
			}
		}
		throw new ServletException("No adapter for handler [" + handler +
				"]: The DispatcherServlet configuration needs to include a HandlerAdapter that supports this handler");
	}

	/**
	 * Determine an error ModelAndView via the registered HandlerExceptionResolvers.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or {@code null} if none chosen at the time of the exception
	 * (for example, if multipart resolution failed)
	 * @param ex the exception that got thrown during handler execution
	 * @return a corresponding ModelAndView to forward to
	 * @throws Exception if no error ModelAndView found
	 * todo 异常视图的处理
	 * todo 这里Spring主要的工作就是将逻辑引导至 HandlerExceptionResolver 类的 resolveException 方法
	 */
	@Nullable
	protected ModelAndView processHandlerException(HttpServletRequest request, HttpServletResponse response,
			@Nullable Object handler, Exception ex) throws Exception {

		// Check registered HandlerExceptionResolvers...
		ModelAndView exMv = null;
		//todo 遍历异常处理器，处理对应异常
		if (this.handlerExceptionResolvers != null) {
			for (HandlerExceptionResolver handlerExceptionResolver : this.handlerExceptionResolvers) {
				exMv = handlerExceptionResolver.resolveException(request, response, handler, ex);
				if (exMv != null) {
					break;
				}
			}
		}
		if (exMv != null) {
			if (exMv.isEmpty()) {
				request.setAttribute(EXCEPTION_ATTRIBUTE, ex);
				return null;
			}
			// We might still need view name translation for a plain error model...
			if (!exMv.hasView()) {
				String defaultViewName = getDefaultViewName(request);
				if (defaultViewName != null) {
					exMv.setViewName(defaultViewName);
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Handler execution resulted in exception - forwarding to resolved error view: " + exMv, ex);
			}
			WebUtils.exposeErrorRequestAttributes(request, ex, getServletName());
			return exMv;
		}

		throw ex;
	}

	/**
	 * Render the given ModelAndView.
	 * <p>This is the last stage in handling a request. It may involve resolving the view by name.
	 * @param mv the ModelAndView to render
	 * @param request current HTTP servlet request
	 * @param response current HTTP servlet response
	 * @throws ServletException if view is missing or cannot be resolved
	 * @throws Exception if there's a problem rendering the view
	 * todo 根据视图跳转页面
	 */
	protected void render(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// Determine locale for request and apply it to the response.
		Locale locale =
				(this.localeResolver != null ? this.localeResolver.resolveLocale(request) : request.getLocale());
		response.setLocale(locale);

		View view;
		//todo 获取视图名称
		String viewName = mv.getViewName();
		if (viewName != null) {
			// We need to resolve the view name.
			//todo 通过视图解析器对视图名称，进行解析，得到具体的视图对象 1 解析视图名称
			view = resolveViewName(viewName, mv.getModelInternal(), locale, request);
			if (view == null) {
				throw new ServletException("Could not resolve view with name '" + mv.getViewName() +
						"' in servlet with name '" + getServletName() + "'");
			}
		}
		else {
			// No need to lookup: the ModelAndView object contains the actual View object.
			//todo 通过ModelAndView对象直接获取实际的视图对象
			view = mv.getView();
			if (view == null) {
				throw new ServletException("ModelAndView [" + mv + "] neither contains a view name nor a " +
						"View object in servlet with name '" + getServletName() + "'");
			}
		}

		// Delegate to the View object for rendering.
		if (logger.isDebugEnabled()) {
			logger.debug("Rendering view [" + view + "] in DispatcherServlet with name '" + getServletName() + "'");
		}
		try {
			if (mv.getStatus() != null) {
				response.setStatus(mv.getStatus().value());
			}
			//todo 调用具体的视图对象，完成渲染工作 页面跳转
			view.render(mv.getModelInternal(), request, response);
		}
		catch (Exception ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Error rendering view [" + view + "] in DispatcherServlet with name '" +
						getServletName() + "'", ex);
			}
			throw ex;
		}
	}

	/**
	 * Translate the supplied request into a default view name.
	 * @param request current HTTP servlet request
	 * @return the view name (or {@code null} if no default found)
	 * @throws Exception if view name translation failed
	 */
	@Nullable
	protected String getDefaultViewName(HttpServletRequest request) throws Exception {
		return (this.viewNameTranslator != null ? this.viewNameTranslator.getViewName(request) : null);
	}

	/**
	 * Resolve the given view name into a View object (to be rendered).
	 * <p>The default implementations asks all ViewResolvers of this dispatcher.
	 * Can be overridden for custom resolution strategies, potentially based on
	 * specific model attributes or request parameters.
	 * @param viewName the name of the view to resolve
	 * @param model the model to be passed to the view
	 * @param locale the current locale
	 * @param request current HTTP servlet request
	 * @return the View object, or {@code null} if none found
	 * @throws Exception if the view cannot be resolved
	 * (typically in case of problems creating an actual View object)
	 * @see ViewResolver#resolveViewName
	 * todo 解析视图名称
	 */
	@Nullable
	protected View resolveViewName(String viewName, @Nullable Map<String, Object> model,
			Locale locale, HttpServletRequest request) throws Exception {
		//todo 我们以 org.Springframework.web.servlet.view.lntemalResourceViewResolver
		//todo 为例来分析 ViewResolver逻辑的解析过程，其中resolveViewName函数的实现是在其父类AbstractCachingViewResolver 中完成的
		if (this.viewResolvers != null) {
			for (ViewResolver viewResolver : this.viewResolvers) {
				View view = viewResolver.resolveViewName(viewName, locale);
				if (view != null) {
					return view;
				}
			}
		}
		return null;
	}

	private void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerExecutionChain mappedHandler, Exception ex) throws Exception {

		if (mappedHandler != null) {
			mappedHandler.triggerAfterCompletion(request, response, ex);
		}
		throw ex;
	}

	/**
	 * Restore the request attributes after an include.
	 * @param request current HTTP request
	 * @param attributesSnapshot the snapshot of the request attributes before the include
	 */
	@SuppressWarnings("unchecked")
	private void restoreAttributesAfterInclude(HttpServletRequest request, Map<?,?> attributesSnapshot) {
		// Need to copy into separate Collection here, to avoid side effects
		// on the Enumeration when removing attributes.
		Set<String> attrsToCheck = new HashSet<>();
		Enumeration<?> attrNames = request.getAttributeNames();
		while (attrNames.hasMoreElements()) {
			String attrName = (String) attrNames.nextElement();
			if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
				attrsToCheck.add(attrName);
			}
		}

		// Add attributes that may have been removed
		attrsToCheck.addAll((Set<String>) attributesSnapshot.keySet());

		// Iterate over the attributes to check, restoring the original value
		// or removing the attribute, respectively, if appropriate.
		for (String attrName : attrsToCheck) {
			Object attrValue = attributesSnapshot.get(attrName);
			if (attrValue == null){
				request.removeAttribute(attrName);
			}
			else if (attrValue != request.getAttribute(attrName)) {
				request.setAttribute(attrName, attrValue);
			}
		}
	}

	private static String getRequestUri(HttpServletRequest request) {
		String uri = (String) request.getAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE);
		if (uri == null) {
			uri = request.getRequestURI();
		}
		return uri;
	}

}
