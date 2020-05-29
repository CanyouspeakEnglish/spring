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

package org.springframework.aop.config;

import org.w3c.dom.Element;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.lang.Nullable;

/**
 * Utility class for handling registration of auto-proxy creators used internally
 * by the '{@code aop}' namespace tags.
 *
 * <p>Only a single auto-proxy creator can be registered and multiple tags may wish
 * to register different concrete implementations. As such this class delegates to
 * {@link AopConfigUtils} which wraps a simple escalation protocol. Therefore classes
 * may request a particular auto-proxy creator and know that class, <i>or a subclass
 * thereof</i>, will eventually be resident in the application context.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.0
 * @see AopConfigUtils
 * todo aop命名空间工具类
 */
public abstract class AopNamespaceUtils {

	/**
	 * The {@code proxy-target-class} attribute as found on AOP-related XML tags.
	 */
	public static final String PROXY_TARGET_CLASS_ATTRIBUTE = "proxy-target-class";

	/**
	 * The {@code expose-proxy} attribute as found on AOP-related XML tags.
	 */
	private static final String EXPOSE_PROXY_ATTRIBUTE = "expose-proxy";

	/**
	 * todo 注册InfrastructureAdvisorAutoProxyCreator类型的bean
	 * @param parserContext
	 * @param sourceElement
	 */
	public static void registerAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		BeanDefinition beanDefinition = AopConfigUtils.registerAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	public static void registerAspectJAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {
		// 向IoC容器中注册一个AspectJAwareAdvisorAutoProxyCreator 类的定义信息
		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		// 是否设置CGLib代理以及是否暴露最终的代理
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	/**
	 *  todo 注册 AnnotationAwareAspectJAutoProxyCreator
	 * @param parserContext
	 * @param sourceElement
	 */
	public static void registerAspectJAnnotationAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {
		//todo 注册或升级 AutoProxyCreator 定义 beanName为 org.Springframework.aop.config.internalAutoProxyCreator的BeanDefinition
		//todo 1.注册或者升级 AnnotationAwareAspectJAutoProxyCreator
		//todo 对于AOP的实现，基本上都是靠AnnotationAwareAspectJAutoProxyCreator去完成，它可以根据@Point注解定义的切点来自动代理相
		//todo 匹配的bean.但是为了配置简便，Spring使用了自定义配置来帮助我们自动注册AnnotationAwareAspectJAutoProxyCreator,
		//todo 其注册过程就是在 这里实现的
		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		//todo 对于 proxy-target-class 以及 expose-proxy 属性的处理
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		//todo 注册组件并通知.便于监听器做进一步处理
		//todo 其中 beanDefinition 的 className 为 AnnotationAwareAspectJAutoProxyCreator
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	/**
	 *
	 * @param registry
	 * @param sourceElement
	 * todo 对于 proxy-target-class 以及 expose-proxy 属性的处理
	 */
	private static void useClassProxyingIfNecessary(BeanDefinitionRegistry registry, @Nullable Element sourceElement) {
		if (sourceElement != null) {
			//todo 是否使用CGLib代理 对于 proxy-target-class 属性的处理
			//todo SpringAOP部分使用JDK动态代理或者CGLIB来为目标对象创建代理.(建议尽量使用JDK的动态代理),如果被代理的目标对象实现了
			//todo 至少一个接口， 则会使用JDK动态代理。所有该目标类型实现的接口都将被代理。若该目标对象没有实现任何接口，
			//todo 则创建一个CGLIB代理。如果你希望强制使用CGLIB代理，(例如希望代理目标对象的所有方法，而不只是实现自接口的方法)
			//todo 那也可以.但是需要考虑 以下两个问题
			//todo 1 无法通知(advise) Final方法，因为它们不能被覆写
			//todo 2 你需要将CGLIB二进制发行包放在classpath下面
			//todo <aop:config proxy-target-class="true">...</aop:config>
			//todo 当需要使用CGLIB代理和@AspectJ自动代理支持.可以按照以下方式设置<aop:aspectj-autoproxy>的 proxy-target-class属性：
			//todo <aop:aspectj-autoproxy proxy-target-class="true">
			//todo JDK动态代理：其代理对象必须是某个接口的实现，它是通过在运行期间创建一个接口的实现类来完成对目标对象的代理
			//todo CGLIB代理：实现原理类似于JDK动态代理，只是它在运行期间生成的代理对象是 针对目标类扩展的子类.CGLIB是高效的代码生成包，
			//todo 底层是依靠ASM (开源的Java 字节码编辑类库)操作字节码实现的，性能比JDK强
			boolean proxyTargetClass = Boolean.parseBoolean(sourceElement.getAttribute(PROXY_TARGET_CLASS_ATTRIBUTE));
			if (proxyTargetClass) {
				AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
			}
			//todo  是否暴露最终的代理 对于 expose-proxy 属性的处理
			//todo 有时候目标对象内部的自我调用将无法实施切面中的増强 自身调用事务不生效
			//todo <aop:aspectj-autoproxy expose-proxy="true"/>
			//todo AopContext.currentProxy() 拿出代理类进行调用
			boolean exposeProxy = Boolean.parseBoolean(sourceElement.getAttribute(EXPOSE_PROXY_ATTRIBUTE));
			if (exposeProxy) {
				//todo  强制使用的过程其实也是一个属性设置的过程
				AopConfigUtils.forceAutoProxyCreatorToExposeProxy(registry);
			}
		}
	}

	private static void registerComponentIfNecessary(@Nullable BeanDefinition beanDefinition, ParserContext parserContext) {
		if (beanDefinition != null) {
			BeanComponentDefinition componentDefinition =
					new BeanComponentDefinition(beanDefinition, AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME);
			parserContext.registerComponent(componentDefinition);
		}
	}

}
