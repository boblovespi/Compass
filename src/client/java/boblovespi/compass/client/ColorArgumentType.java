package boblovespi.compass.client;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import java.util.Collection;
import java.util.List;

public class ColorArgumentType implements ArgumentType<Integer>
{
	private static final DynamicCommandExceptionType READER_INVALID_COLOR = new DynamicCommandExceptionType(value -> new LiteralMessage("Invalid hex color '" + value + "'"));
	private static final Collection<String> SUGGESTIONS = List.of("0xFF0000", "#00FF00");

	public static ColorArgumentType of()
	{
		return new ColorArgumentType();
	}

	@Override
	public Integer parse(StringReader reader) throws CommandSyntaxException
	{
		var str = new StringBuilder();
		while (reader.canRead() && "xX#0123456789abcdefABCDEF".indexOf(reader.peek()) != -1)
		{
			str.append(reader.read());
		}
		var i = Utils.tryParseInt(str.toString(), Integer::decode);
		if (i.isEmpty())
			throw READER_INVALID_COLOR.create(str.toString());
		return i.getAsInt();
	}

	@Override
	public Collection<String> getExamples()
	{
		return SUGGESTIONS;
	}
}
