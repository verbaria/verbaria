package org.zanata.util;

import java.beans.PropertyDescriptor;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.hibernate.Hibernate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Primitives;

public class JPACopier {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(JPACopier.class);

    private static final List<String> COMMON_IGNORED_FIELDS = ImmutableList
            .<String> builder().add("id").add("creationDate").build();

    private static Map<Class<?>, List<String>> FIELDS_TO_COPY =
            Maps.newConcurrentMap();

    @SuppressWarnings("unchecked")
    public static <T> T copyBean(@NonNull T fromBean,
            String... ignoreProperties)
            throws IllegalAccessException, InstantiationException,
            InvocationTargetException, NoSuchMethodException {
        Preconditions.checkNotNull(fromBean);
        Class<?> beanClass = Hibernate.getClass(fromBean);
        Object copy;
        try {
            copy = beanClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new InstantiationException(e.getMessage());
        }
        copy = copyBean(fromBean, copy, ignoreProperties);
        return (T) copy;
    }

    @SuppressWarnings("unchecked")
    public static <T> T copyBean(@NonNull T fromBean, @NonNull Object toBean,
            String... ignoreProperties)
            throws IllegalAccessException, InstantiationException,
            InvocationTargetException, NoSuchMethodException {
        Preconditions.checkNotNull(fromBean);
        Preconditions.checkNotNull(toBean);
        if (isPrimitiveOrString(fromBean)) {
            return fromBean;
        }
        BeanUtilsBean beanUtilsBean = BeanUtilsBean.getInstance();
        if (isCollectionType(fromBean.getClass())) {
            return (T) createNewCollection(fromBean.getClass(), fromBean);
        }
        List<String> ignoreList = Lists.newArrayList(ignoreProperties);
        Map<String, Object> propertiesMap =
                beanUtilsBean.getPropertyUtils().describe(fromBean);
        for (Map.Entry<String, Object> entry : propertiesMap.entrySet()) {
            String property = entry.getKey();
            Object value = entry.getValue();
            if (!shouldCopy(beanUtilsBean.getPropertyUtils(), toBean, property,
                    ignoreList)) {
                continue;
            }
            if (value != null && isJPACopyProperty(fromBean, property)) {
                value = copyBean(value);
            }
            copyProperty(beanUtilsBean, toBean, property, value);
        }
        return (T) toBean;
    }

    private static boolean shouldCopy(PropertyUtilsBean propertyUtilsBean,
            Object toBean, String property, List<String> ignoreList) {
        return propertyUtilsBean.isWriteable(toBean, property)
                && !ignoreList.contains(property)
                && !COMMON_IGNORED_FIELDS.contains(property);
    }

    private static boolean isJPACopyProperty(Object bean, String property)
            throws IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        if (!FIELDS_TO_COPY.containsKey(bean.getClass())) {
            FIELDS_TO_COPY.put(bean.getClass(), getJPACopierFields(bean));
        }
        return FIELDS_TO_COPY.get(bean.getClass()).contains(property);
    }

    private static boolean isPrimitiveOrString(Object obj) {
        return obj instanceof String
                || Primitives.isWrapperType(Primitives.wrap(obj.getClass()));
    }

    private static void copyProperty(BeanUtilsBean beanUtilsBean, Object toBean,
            String property, Object value) throws InvocationTargetException,
            IllegalAccessException, NoSuchMethodException {
        Class<?> propertyType = beanUtilsBean.getPropertyUtils()
                .getPropertyDescriptor(toBean, property).getPropertyType();
        if (isCollectionType(propertyType)) {
            value = createNewCollection(propertyType, value);
        }
        beanUtilsBean.copyProperty(toBean, property, value);
    }

    private static boolean isCollectionType(Class<?> clazz) {
        return clazz == List.class || clazz == Set.class || clazz == Map.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object createNewCollection(Class clazz, Object value) {
        if (value != null) {
            if (clazz == List.class) {
                List<Object> list = Lists.newArrayList();
                list.addAll((List<Object>) value);
                return list;
            } else if (clazz == Set.class) {
                Set<Object> set = Sets.newHashSet();
                set.addAll((Set<Object>) value);
                return set;
            } else if (clazz == Map.class) {
                Map<Object, Object> map = Maps.newHashMap();
                map.putAll((Map<Object, Object>) value);
                return map;
            }
        }
        return value;
    }

    private static List<String> getJPACopierFields(Object bean)
            throws IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        PropertyUtilsBean propertyUtilsBean =
                BeanUtilsBean.getInstance().getPropertyUtils();
        List<String> properties = Lists.newCopyOnWriteArrayList();
        Class<?> noProxyBean = Hibernate.getClass(bean);
        Map<String, Object> propertiesMap = propertyUtilsBean.describe(bean);
        for (String property : propertiesMap.keySet()) {
            try {
                PropertyDescriptor descriptor =
                        propertyUtilsBean.getPropertyDescriptor(bean, property);
                String methodName =
                        propertyUtilsBean.getReadMethod(descriptor).getName();
                Method getterMethod = noProxyBean.getMethod(methodName);
                if (isUseJPACopier(getterMethod)) {
                    properties.add(property);
                    continue;
                }
            } catch (NoSuchMethodException e) {
                log.debug("Read method inaccessible for {} in class-{}",
                        property, noProxyBean.getName());
            }
            Field field = FieldUtils.getField(bean.getClass(), property, true);
            if (isUseJPACopier(field)) {
                properties.add(property);
            }
        }
        return properties;
    }

    private static boolean isUseJPACopier(AccessibleObject accessibleObject) {
        if (accessibleObject == null) {
            return false;
        }
        if (accessibleObject.isAnnotationPresent(OneToOne.class)) {
            return true;
        } else if (accessibleObject.isAnnotationPresent(OneToMany.class)
                && StringUtils.isNotEmpty(accessibleObject
                        .getAnnotation(OneToMany.class).mappedBy())) {
            return true;
        }
        return false;
    }

    private JPACopier() {}
}
