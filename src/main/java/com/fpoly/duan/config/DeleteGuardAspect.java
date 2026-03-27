package com.fpoly.duan.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.ResolvableType;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;

import com.fpoly.duan.service.DeleteGuardService;

import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
public class DeleteGuardAspect {

    private final DeleteGuardService deleteGuardService;
    private final Map<Class<?>, Class<?>> repositoryEntityCache = new ConcurrentHashMap<>();

    @Around("execution(* org.springframework.data.repository.CrudRepository+.deleteById(..))")
    public Object guardDeleteById(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (args.length == 1 && args[0] != null) {
            Class<?> entityClass = resolveEntityClass(joinPoint.getTarget().getClass());
            if (entityClass != null) {
                deleteGuardService.assertNoReferences(entityClass, args[0]);
            }
        }
        return joinPoint.proceed();
    }

    @Around("execution(* org.springframework.data.repository.CrudRepository+.delete(..))")
    public Object guardDeleteEntity(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (args.length == 1 && args[0] != null) {
            Object entity = args[0];
            Object id = deleteGuardService.resolveEntityId(entity);
            if (id != null) {
                deleteGuardService.assertNoReferences(entity.getClass(), id);
            }
        }
        return joinPoint.proceed();
    }

    private Class<?> resolveEntityClass(Class<?> repositoryProxyClass) {
        return repositoryEntityCache.computeIfAbsent(repositoryProxyClass, this::doResolveEntityClass);
    }

    private Class<?> doResolveEntityClass(Class<?> repositoryProxyClass) {
        for (Class<?> itf : repositoryProxyClass.getInterfaces()) {
            ResolvableType type = ResolvableType.forClass(itf).as(Repository.class);
            if (type != ResolvableType.NONE) {
                Class<?> entityClass = type.getGeneric(0).resolve();
                if (entityClass != null && entityClass != Object.class) {
                    return entityClass;
                }
            }
        }
        return null;
    }
}
