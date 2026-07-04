package boblovespi.compass.client;

public class OptionalFloat
{
	private boolean hasValue;
	private float value;

	private static final OptionalFloat EMPTY = new OptionalFloat(false, 0);

	public OptionalFloat(boolean hasValue, float value)
	{
		this.hasValue = hasValue;
		this.value = value;
	}

	public static OptionalFloat empty()
	{
		return EMPTY;
	}


	public static OptionalFloat of(float value)
	{
		return new OptionalFloat(true, value);
	}

	public boolean isEmpty()
	{
		return !hasValue;
	}

	public float get()
	{
		if (isEmpty())
			throw new RuntimeException("cannot get value of an absent optional");
		return value;
	}
}
