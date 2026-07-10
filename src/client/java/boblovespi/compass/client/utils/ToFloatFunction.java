package boblovespi.compass.client.utils;

@FunctionalInterface
public interface ToFloatFunction<T>
{
	float apply(T t);
}
