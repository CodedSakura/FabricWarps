package eu.codedsakura.mods;

import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;

public class TextUtils {
    public static MutableText join(List<Text> values, Text joiner) {
        MutableText out = LiteralText.EMPTY.copy();
        for (int i = 0; i < values.size(); i++) {
            out.append(values.get(i));
            if (i < values.size() - 1) out.append(joiner);
        }
        return out;
    }
}
