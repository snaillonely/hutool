package cn.hutool.core.annotation;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 合成注解代理类
 *
 * @author huangchengxing
 */
class SyntheticAnnotationProxy implements InvocationHandler {

	private final SynthesizedAggregateAnnotation aggregateAnnotation;
	private final SynthesizedAnnotation annotation;
	private final Map<String, BiFunction<Method, Object[], Object>> methods;

	SyntheticAnnotationProxy(SynthesizedAggregateAnnotation aggregateAnnotation, SynthesizedAnnotation annotation) {
		Assert.notNull(aggregateAnnotation, "aggregateAnnotation must not null");
		Assert.notNull(annotation, "annotation must not null");
		this.aggregateAnnotation = aggregateAnnotation;
		this.annotation = annotation;
		this.methods = new HashMap<>(9);
		loadMethods();
	}

	/**
	 * 创建一个代理注解，生成的代理对象将是{@link SyntheticProxyAnnotation}与指定的注解类的子类。
	 * <ul>
	 *     <li>当作为{@code annotationType}所指定的类型使用时，其属性将通过合成它的{@link SynthesizedAggregateAnnotation}获取；</li>
	 *     <li>当作为{@link SyntheticProxyAnnotation}或{@link SynthesizedAnnotation}使用时，将可以获得原始注解实例的相关信息；</li>
	 * </ul>
	 *
	 * @param annotationType      注解类型
	 * @param aggregateAnnotation 合成注解
	 * @return 代理注解
	 */
	@SuppressWarnings("unchecked")
	static <T extends Annotation> T create(
		Class<T> annotationType, SynthesizedAggregateAnnotation aggregateAnnotation, SynthesizedAnnotation annotation) {
		if (ObjectUtil.isNull(annotation)) {
			return null;
		}
		final SyntheticAnnotationProxy proxyHandler = new SyntheticAnnotationProxy(aggregateAnnotation, annotation);
		if (ObjectUtil.isNull(annotation)) {
			return null;
		}
		return (T) Proxy.newProxyInstance(
			annotationType.getClassLoader(),
			new Class[]{annotationType, SyntheticProxyAnnotation.class},
			proxyHandler
		);
	}

	static boolean isProxyAnnotation(Class<?> targetClass) {
		return ClassUtil.isAssignable(SyntheticProxyAnnotation.class, targetClass);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		return Opt.ofNullable(methods.get(method.getName()))
			.map(m -> m.apply(method, args))
			.orElseGet(() -> ReflectUtil.invoke(this, method, args));
	}

	// ========================= 代理方法 =========================

	void loadMethods() {
		methods.put("toString", (method, args) -> proxyToString());
		methods.put("hashCode", (method, args) -> proxyHashCode());
		methods.put("getSynthesizedAnnotationAggregator", (method, args) -> proxyGetSynthesizedAnnotationAggregator());
		methods.put("getSynthesizedAnnotation", (method, args) -> proxyGetSynthesizedAnnotation());
		methods.put("getRoot", (method, args) -> annotation.getRoot());
		methods.put("getVerticalDistance", (method, args) -> annotation.getVerticalDistance());
		methods.put("getHorizontalDistance", (method, args) -> annotation.getHorizontalDistance());
		methods.put("hasAttribute", (method, args) -> annotation.hasAttribute((String)args[0], (Class<?>)args[1]));
		methods.put("getAttributes", (method, args) -> annotation.getAttributes());
		methods.put("setAttribute", (method, args) -> {
			throw new UnsupportedOperationException("proxied annotation can not reset attributes");
		});
		methods.put("getAttributeValue", (method, args) -> annotation.getAttributeValue((String)args[0]));
		methods.put("annotationType", (method, args) -> annotation.annotationType());
		for (final Method declaredMethod : ClassUtil.getDeclaredMethods(annotation.getAnnotation().annotationType())) {
			methods.put(declaredMethod.getName(), (method, args) -> proxyAttributeValue(method));
		}
	}

	private String proxyToString() {
		final String attributes = Stream.of(ClassUtil.getDeclaredMethods(annotation.getAnnotation().annotationType()))
			.filter(AnnotationUtil::isAttributeMethod)
			.map(method -> CharSequenceUtil.format("{}={}", method.getName(), aggregateAnnotation.getAttribute(method.getName(), method.getReturnType())))
			.collect(Collectors.joining(", "));
		return CharSequenceUtil.format("@{}({})", annotation.annotationType().getName(), attributes);
	}

	private int proxyHashCode() {
		return Objects.hash(aggregateAnnotation, annotation);
	}

	private Object proxyGetSynthesizedAnnotationAggregator() {
		return aggregateAnnotation;
	}

	private Object proxyGetSynthesizedAnnotation() {
		return annotation;
	}

	private Object proxyAttributeValue(Method attributeMethod) {
		return aggregateAnnotation.getAttribute(attributeMethod.getName(), attributeMethod.getReturnType());
	}

	/**
	 * 通过代理类生成的合成注解
	 *
	 * @author huangchengxing
	 */
	interface SyntheticProxyAnnotation extends SynthesizedAnnotation {

		/**
		 * 获取该注解所属的合成注解
		 *
		 * @return 合成注解
		 */
		SynthesizedAggregateAnnotation getSynthesizedAnnotationAggregator();

		/**
		 * 获取该代理注解对应的已合成注解
		 *
		 * @return 理注解对应的已合成注解
		 */
		SynthesizedAnnotation getSynthesizedAnnotation();

	}

}
