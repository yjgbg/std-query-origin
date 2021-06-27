package com.github.yjgbg.demo.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import com.google.common.collect.Sets;
import io.vavr.CheckedFunction1;
import io.vavr.Tuple2;
import io.vavr.collection.Stream;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.vavr.API.Stream;
import static io.vavr.API.Tuple;

public class MappingGraphedOutputEntity2HttpMessageConverter extends MappingJackson2HttpMessageConverter {
	private final Supplier<String[]> includes;
	private final Supplier<String[]> excludes;

	public MappingGraphedOutputEntity2HttpMessageConverter(
			Supplier<String[]> includes, Supplier<String[]> excludes, ObjectMapper om) {
		super(om);
		this.excludes = () -> {
			final var res = excludes.get();
			return res != null ? res : new String[0];
		};
		this.includes = () -> {
			final var res = includes.get();
			return res != null ? res : new String[0];
		};
	}

	@Override
	public void writeInternal(@NonNull Object object, @Nullable Type type,
	                          @NonNull HttpOutputMessage httpOutputMessage)
			throws IOException, HttpMessageNotWritableException {
		super.writeInternal(toMap(object, includes.get(), excludes.get()), type, httpOutputMessage);
	}

	/**
	 * @param object   此处允许传入的应当不包括key不为string的类型，但java的类型系统无法写出这样的约束，因此使用注释来描述
	 * @param includes 在转换为map时需要包含的字段（只处理类型为graphedEntity和Map）
	 * @return 如果参数object为集合，则返回集合；如果是map，则返回一个只包含
	 */
	private Object toMap(@NonNull Object object, String[] includes, String[] excludes) {
		if (object instanceof Iterable) { //集合被穿透
			final var list = new ArrayList<>();
			((Iterable<?>) object).forEach(x -> list.add(toMap(x, includes, excludes)));
			return list;
		}
		final var clazz = object.getClass();
		final var formatted =  formatIncludes(clazz, includes,excludes);
		final var includePathAndSubPath = formatted._1()
				.toMap(Function.identity(),path -> Stream(includes)
								.filter(x -> x.startsWith(path+"."))
								.map(x -> x.substring(path.length()+1))
						.toJavaArray(String[]::new))
				.toSet();
		if (object instanceof Map) { // map的处理方式:看作GraphedEntity对象来处理
			final var map = (Map<?, ?>) object;
			final var res = new HashMap<String, Object>();
			includePathAndSubPath.forEach(pair -> {
				final var value = map.get(pair._1());
				final var subExclude = Arrays.stream(excludes).filter(x -> x.startsWith(pair._1()+"."))
						.map(x -> x.substring(pair._1().length()+1))
						.toArray(String[]::new);
				res.put(pair._1(), toMap(value, pair._2(), subExclude));
				formatted._2().filter(x -> !x.contains("."))
						.forEach(e -> res.put(e,"_exclude"));
			});
			return res;
		}
		if (!(object instanceof GraphedEntity)) return object;// 普通对象不做处理
		// GraphedEntity对象:映射为map，map中只包含includes中提到属性
		final var res = includePathAndSubPath.map(CheckedFunction1.<Tuple2<String, String[]>, Tuple2<String, Object>>of(
				tuple -> {
					final var fieldName = tuple._1();
					final var getter = "get" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, fieldName);
					final var method = clazz.getMethod(getter);
					final var fieldValue = method.invoke(object);
					final var subExclude = Arrays.stream(excludes).filter(x -> x.startsWith(tuple._1()+"."))
							.map(x -> x.substring(tuple._1().length()+1))
							.toArray(String[]::new);
					final var afterProcess = toMap(fieldValue, tuple._2(),subExclude);
					return tuple.map2(x -> afterProcess);
				}
		).unchecked()).toJavaMap(HashMap::new, Tuple2::_1, Tuple2::_2);
		formatted._2().filter(x -> !x.contains("."))
				.forEach(e -> res.put(e,"_excluded"));
		return res;
	}

	private static final Map<Class<?>, Set<String>> CLASS_AND_FIELD_CACHE = new HashMap<>();
	private static final Map<Class<?>, Set<String>> CLASS_AND_SIMPLE_FIELD_CACHE = new HashMap<>();

	private static Set<String> fields(Class<?> c) {
		return CLASS_AND_FIELD_CACHE.computeIfAbsent(c, clz -> Arrays.stream(clz.getMethods())
				.filter(x -> x.getName().startsWith("get"))
				.filter(x -> !x.getName().equals("getClass"))
				.filter(x -> x.getParameterCount() == 0)
				.map(MappingGraphedOutputEntity2HttpMessageConverter::fieldNameByGetter)
				.collect(Collectors.toSet()));
	}

	private static Set<String> simpleFields(Class<?> c) {
		return CLASS_AND_SIMPLE_FIELD_CACHE.computeIfAbsent(c, clz -> Arrays.stream(clz.getMethods())
				.filter(x -> x.getName().startsWith("get"))
				.filter(x -> !x.getName().equals("getClass"))
				.filter(x -> x.getParameterCount() == 0)
				.filter(x -> !GraphedEntity.class.isAssignableFrom(x.getReturnType()))
				.map(MappingGraphedOutputEntity2HttpMessageConverter::fieldNameByGetter)
				.collect(Collectors.toSet()));
	}

	private static String fieldNameByGetter(Method getter) {
		final var x = getter.getName().substring(3);
		return Character.toLowerCase(x.charAt(0)) + x.substring(1);
	}

	private static Tuple2<Stream<String>, Stream<String>>
	formatIncludes(Class<?> clazz, String[] includes, String[] excludes) {
		final var fields = fields(clazz);
		// 默认include
		final var simpleFields = simpleFields(clazz);
		// 默认exclude
		final var complexFields = Sets.difference(fields, simpleFields);
		// custom include
		final var formattedInclude = Stream(includes)
				.filter(x -> fields.contains(x.substring(0, Math.max(x.indexOf('.'), x.length()))));
		// custom exclude
		final var formattedExclude = Stream(excludes)
				.filter(x -> fields.contains(x.substring(0, Math.max(x.indexOf('.'), x.length()))));
		final var i = Sets.union(Sets.difference(simpleFields,formattedExclude.toJavaSet()),formattedInclude.toJavaSet());
		final var e = Sets.union(Sets.difference(complexFields,formattedInclude.toJavaSet()),formattedExclude.toJavaSet());
		return Tuple(Stream.ofAll(i),Stream.ofAll(e));
	}
}
