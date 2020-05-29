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

import java.util.ArrayList;
import java.util.List;

import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.InfrastructureAdvisorAutoProxyCreator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Utility class for handling registration of AOP auto-proxy creators.
 *
 * <p>Only a single auto-proxy creator can be registered yet multiple concrete
 * implementations are available. Therefore this class wraps a simple escalation
 * protocol, allowing classes to request a particular auto-proxy creator and know
 * that class, {@code or a subclass thereof}, will eventually be resident
 * in the application context.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.5
 * @see AopNamespaceUtils
 */
public abstract class AopConfigUtils {

	/**
	 * The bean name of the internally managed auto-proxy creator.
	 */
	public static final String AUTO_PROXY_CREATOR_BEAN_NAME =
			"org.springframework.aop.config.internalAutoProxyCreator";

	/**
	 * Stores the auto proxy creator classes in escalation order.
	 */
	private static final List<Class<?>> APC_PRIORITY_LIST = new ArrayList<>();

	/**
	 * Setup the escalation list.
	 */
	static {
		APC_PRIORITY_LIST.add(InfrastructureAdvisorAutoProxyCreator.class);
		APC_PRIORITY_LIST.add(AspectJAwareAdvisorAutoProxyCreator.class);
		APC_PRIORITY_LIST.add(AnnotationAwareAspectJAutoProxyCreator.class);
	}


	@Nullable
	public static BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAutoProxyCreatorIfNecessary(registry, null);
	}

	@Nullable
	public static BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry,
			@Nullable Object source) {

		return registerOrEscalateApcAsRequired(InfrastructureAdvisorAutoProxyCreator.class, registry, source);
	}

	@Nullable
	public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAspectJAutoProxyCreatorIfNecessary(registry, null);
	}

	@Nullable
	public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry,
			@Nullable Object source) {
		//todo  注册AspectJAwareAdvisorAutoProxyCreator的BeanDefinition到BeanFactory中
		//todo AspectJAwareAdvisorAutoProxyCreator 产生Aop 代理对象 实现类BeanPostProcessor
		return registerOrEscalateApcAsRequired(AspectJAwareAdvisorAutoProxyCreator.class, registry, source);
	}

	@Nullable
	public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
		return registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry, null);
	}

	/**
	 *
	 * @param registry
	 * @param source
	 * @return
	 * todo 注册或者升级 AnnotationAwareAspectJAutoProxyCreator
	 */
	@Nullable
	public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry,
			@Nullable Object source) {

		return registerOrEscalateApcAsRequired(AnnotationAwareAspectJAutoProxyCreator.class, registry, source);
	}

	public static void forceAutoProxyCreatorToUseClassProxying(BeanDefinitionRegistry registry) {
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			definition.getPropertyValues().add("proxyTargetClass", Boolean.TRUE);
		}
	}

	/**
	 *
	 * @param registry
	 * todo 强制使用的过程其实也是一个属性设置的过程
	 */
	public static void forceAutoProxyCreatorToExposeProxy(BeanDefinitionRegistry registry) {
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			definition.getPropertyValues().add("exposeProxy", Boolean.TRUE);
		}
	}

	/**
	 *
	 * @param cls
	 * @param registry
	 * @param source
	 * @return
	 *todo  注册或者升级 AnnotationAwareAspectJAutoProxyCreator
	 * 实现了自动注册AnnotationAwareAspectJAutoProxyCreator类的功能，同时这里
	 还涉及了一个优先级的问题.如果已经存在了自动代理创建器.而且存在的自动代理创建器与现在的不一致，那么需要根据优先级来判断到底需要使用哪个
	 */
	@Nullable
	private static BeanDefinition registerOrEscalateApcAsRequired(Class<?> cls, BeanDefinitionRegistry registry,
			@Nullable Object source) {

		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");

		//todo 判断是否BeanDefinitionRegistry中是否有internalAutoProxyCreator对应的BeanDefinition
		//todo 如果已经存在了自动代理创建器且存在的自动代理创建器与现在的不一致那么需要根据优先级来判断到底需耍使用哪
		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
			//todo 获取internalAutoProxyCreator对应的BeanDefinition
			BeanDefinition apcDefinition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
			//todo 如果internalAutoProxyCreator对应的Bean的类名和第一个入参不一样，且考虑是否需要完成覆盖
			if (!cls.getName().equals(apcDefinition.getBeanClassName())) {
				//todo 当前internalAutoProxyCreator对应Bean的优先级
				int currentPriority = findPriorityForClass(apcDefinition.getBeanClassName());
				//todo 需要的优先级也就是第一个入参对应的Bean的优先级
				int requiredPriority = findPriorityForClass(cls);
				//todo 如果需要的优先级，比internalAutoProxyCreator现在对应的Bean的优先级高，则进行替换操作
				if (currentPriority < requiredPriority) {
					//todo 改变bean最重要的就是改变bean所对应的className属性
					apcDefinition.setBeanClassName(cls.getName());
				}
			}
			//todo 如果已经存在自动代理创建器并且与将要创建的一致.那么无需再次创建
			return null;
		}

		RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
		beanDefinition.setSource(source);
		beanDefinition.getPropertyValues().add("order", Ordered.HIGHEST_PRECEDENCE);
		beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		//todo 往BeanFactory注册指定类的BeanDefinition信息,beanName就是org.springframework.aop.config.internalAutoProxyCreator
		registry.registerBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME, beanDefinition);
		return beanDefinition;
	}

	private static int findPriorityForClass(Class<?> clazz) {
		return APC_PRIORITY_LIST.indexOf(clazz);
	}

	private static int findPriorityForClass(@Nullable String className) {
		for (int i = 0; i < APC_PRIORITY_LIST.size(); i++) {
			Class<?> clazz = APC_PRIORITY_LIST.get(i);
			if (clazz.getName().equals(className)) {
				return i;
			}
		}
		throw new IllegalArgumentException(
				"Class name [" + className + "] is not a known auto-proxy creator class");
	}

}
