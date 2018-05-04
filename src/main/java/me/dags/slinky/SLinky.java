package me.dags.slinky;

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import me.dags.textmu.MarkupSpec;
import me.dags.textmu.MarkupTemplate;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author dags <dags@dags.me>
 */
@Plugin(id = "slinky", name = "Slinky", version = "2.0", description = "Links")
public final class SLinky {

    private static final String MATCH = "((ht|f)tp(s?):\\/\\/|www\\.)(([\\w\\-]+\\.){1,}?([\\w\\-.~]+\\/?)*[\\p{Alnum}.,%_=?&#\\-+()\\[\\]\\*$~@!:/{};']*)";
    private static final Pattern MATCH_PATTERN = Pattern.compile(MATCH);
    private static final TypeToken<String> STRING = TypeToken.of(String.class);

    private final ConfigurationLoader<CommentedConfigurationNode> loader;
    private CommentedConfigurationNode config;
    private MarkupTemplate hoverTemplate;
    private MarkupTemplate linkTemplate;

    @Inject
    public SLinky(@DefaultConfig(sharedRoot = false) ConfigurationLoader<CommentedConfigurationNode> loader) {
        this.loader = loader;
        this.reload(null);
    }

    @Listener
    public void reload(GameReloadEvent event) {
        this.config = loadConfig();
        String link = getOrAdd("link_template", STRING, "[yellow,underline,{:hoverTemplate}](linkTemplate)");
        String hover = getOrAdd("hover_template", STRING, "[green,italic](Click to open {url})");
        this.linkTemplate = MarkupSpec.create().template(link);
        this.hoverTemplate = MarkupSpec.create().template(hover);
        saveConfig();
    }

    @Listener(order = Order.LAST)
    public void chat(MessageChannelEvent.Chat event) {
        Text formatted = event.getFormatter().getBody().format();
        Text processed = process(formatted);
        event.getFormatter().setBody(processed);
    }

    private CommentedConfigurationNode loadConfig() {
        try {
            return loader.load();
        } catch (IOException e) {
            e.printStackTrace();
            return loader.createEmptyNode();
        }
    }

    private void saveConfig() {
        try {
            loader.save(config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private <T> T getOrAdd(String key, TypeToken<T> token, T defaultVal) {
        try {
            ConfigurationNode node = config.getNode(key);
            if (node.isVirtual()) {
                node.setValue(token, defaultVal);
                return defaultVal;
            }
            return node.getValue(token);
        } catch (ObjectMappingException e) {
            e.printStackTrace();
            return defaultVal;
        }
    }

    private Text process(Text input) {
        if (MATCH_PATTERN.matcher(input.toPlain()).find()) {
            MarkupTemplate.Applier template = linkTemplate.with("hover_template", hoverTemplate);
            Text.Builder builder = Text.builder();
            processOne(input, builder, template);
            return builder.build();
        }
        return input;
    }

    private void processOne(Text input, Text.Builder rootBuilder, MarkupTemplate.Applier template) {
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
                builder.append(template.with("url", matcher.group()).render());
            }

            if (pos.get() < split.length) {
                builder.append(Text.of(split[pos.get()]));
            }

            builder.applyTo(rootBuilder);
        } else {
            input.toBuilder().removeAll().applyTo(rootBuilder);
        }

        for (Text child : input.getChildren()) {
            processOne(child, rootBuilder, template);
        }
    }
}
