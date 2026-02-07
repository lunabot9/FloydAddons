package floydaddons.not.dogshit.client;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class TimeSliderWidget extends SliderWidget {
    private final Runnable onChange;

    public TimeSliderWidget(int x, int y, int width, int height, Runnable onChange) {
        super(x, y, width, height, Text.literal(""), 0);
        this.onChange = onChange;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Text.literal("Time changer removed"));
    }

    @Override
    protected void applyValue() {
        if (onChange != null) onChange.run();
    }
}
