package boblovespi.compass.client;

@FunctionalInterface
public interface ToFloatFunction<T>
{
	float apply(T t);
}
