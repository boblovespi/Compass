package boblovespi.compass.client.utils;


import java.util.OptionalInt;
import java.util.function.ToIntFunction;

public final class Utils
{
	private Utils()
	{

	}

	public static OptionalInt tryParseInt(String string, ToIntFunction<String> parser)
	{
		var value = 0;
		try
		{
			value = parser.applyAsInt(string);
		}
		catch (Exception e)
		{
			return OptionalInt.empty();
		}
		return OptionalInt.of(value);
	}

	public static OptionalFloat tryParseFloat(String string, ToFloatFunction<String> parser)
	{
		var value = 0f;
		try
		{
			value = parser.apply(string);
		}
		catch (Exception e)
		{
			return OptionalFloat.empty();
		}
		return OptionalFloat.of(value);
	}
}
