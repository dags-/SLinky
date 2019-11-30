package me.dags.slinky;

import com.google.inject.Inject;
import me.dags.config.Config;
import me.dags.text.MUSpec;
import me.dags.text.template.Context;
import me.dags.text.template.MUTemplate;
import me.dags.text.template.Template;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author dags <dags@dags.me>
 */
@Plugin(id = "slinky")
public final class SLinky {

    private static final String MATCH = "((ht|f)tp(s?):\\/\\/|www\\.)(([\\w\\-]+\\.){1,}?([\\w\\-.~]+\\/?)*[\\p{Alnum}.,%_=?&#\\-+()\\[\\]\\*$~@!:/{};']*)";
    private static final Pattern MATCH_PATTERN = Pattern.compile(MATCH);

    private final Config config;
    private Template hover;
    private MUTemplate link;

    @Inject
    public SLinky(@DefaultConfig(sharedRoot = false) Path path) {
        this.config = Config.must(path);
    }

    @Listener
    public void init(GameInitializationEvent event) {
        reload(null);
    }

    @Listener
    public void reload(GameReloadEvent event) {
        try {
            config.reload();
            String linkTemplate = config.get("link", "[link](yellow,underline,{hover},{url})");
            String hoverTemplate = config.get("hover", "[Click to open {url}](green,italic)");
            this.link = MUSpec.create().template(linkTemplate);
            this.hover = Template.parse(hoverTemplate);
            config.saveIfAbsent();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Listener(order = Order.LAST)
    public void chat(MessageChannelEvent.Chat event) {
        Text formatted = event.getFormatter().getBody().format();
        Text processed = process(formatted);
        event.getFormatter().setBody(processed);
    }

    private Text process(Text input) {
        if (MATCH_PATTERN.matcher(input.toPlain()).find()) {
            Text.Builder builder = Text.builder();
            processOne(input, builder);
            return builder.build();
        }
        return input;
    }

    private void processOne(Text input, Text.Builder rootBuilder) {
        String raw = input.toPlainSingle();
        Matcher matcher = SLinky.MATCH_PATTERN.matcher(raw);

        if (matcher.find()) {
            Text.Builder builder = Text.builder().format(input.getFormat());
            input.getHoverAction().ifPresent(builder::onHover);
            input.getClickAction().ifPresent(builder::onClick);
            input.getShiftClickAction().ifPresent(builder::onShiftClick);

            String[] split = SLinky.MATCH_PATTERN.split(raw);
            AtomicInteger pos = new AtomicInteger(0);

            matcher.reset();

            while (matcher.find()) {
                int i = pos.getAndAdd(1);
                if (i < split.length) {
                    builder.append(Text.of(split[i]));
                }
                Context context = Context.create().with("url", matcher.group()).with("hover", hover);
                builder.append(link.with(context).render());
            }

            if (pos.get() < split.length) {
                builder.append(Text.of(split[pos.get()]));
            }

            builder.applyTo(rootBuilder);
        } else {
            input.toBuilder().removeAll().applyTo(rootBuilder);
        }

        for (Text child : input.getChildren()) {
            processOne(child, rootBuilder);
        }
    }

    private Text render(String url) {
        try {
            Context context = Context.create().with("url", url).with("hover", hover);
            StringWriter writer = new StringWriter();
            hover.apply(context, writer);
            context.with("hover", writer.toString());
            return link.with(context).render();
        } catch (IOException e) {
            return Text.of(url);
        }
    }
}
