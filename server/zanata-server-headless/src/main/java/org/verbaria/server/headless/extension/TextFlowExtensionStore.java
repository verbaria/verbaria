package org.verbaria.server.headless.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowExtension;
import org.zanata.rest.dto.extensions.ContentAwareExtension;
import org.zanata.rest.dto.extensions.gettext.TextFlowExtension;
import org.zanata.rest.dto.resource.TextFlow;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class TextFlowExtensionStore {

    private final TextFlowExtensionRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    public TextFlowExtensionStore(TextFlowExtensionRegistry registry) {
        this.registry = registry;
    }

    public void apply(TextFlow dto, HTextFlow htf) {
        if (dto == null || dto.getExtensions() == null) {
            return;
        }
        for (TextFlowExtensionFactory<?> f : registry.all()) {
            TextFlowExtension pojo = dto.getExtensions().findByType(f.pojoType());
            if (pojo != null) {
                put(htf, pojo);
            }
        }
    }

    public void emit(HTextFlow htf, TextFlow dto) {
        for (HTextFlowExtension row : htf.getExtensions()) {
            TextFlowExtension pojo = read(row.getJson());
            if (pojo != null) {
                dto.getExtensions(true).add(pojo);
            }
        }
    }

    public List<TextFlowExtension> all(HTextFlow htf) {
        List<TextFlowExtension> out = new ArrayList<>();
        for (HTextFlowExtension row : htf.getExtensions()) {
            TextFlowExtension pojo = read(row.getJson());
            if (pojo != null) {
                out.add(pojo);
            }
        }
        return out;
    }

    public Optional<String> contentType(HTextFlow htf) {
        for (HTextFlowExtension row : htf.getExtensions()) {
            TextFlowExtension pojo = read(row.getJson());
            if (pojo instanceof ContentAwareExtension content) {
                return Optional.ofNullable(content.getContentType());
            }
        }
        return Optional.empty();
    }

    public <T extends TextFlowExtension> Optional<T> get(HTextFlow htf,
            Class<T> pojoType) {
        TextFlowExtensionFactory<?> f = registry.byPojo(pojoType);
        if (f == null) {
            return Optional.empty();
        }
        HTextFlowExtension row = find(htf, f.type());
        if (row == null) {
            return Optional.empty();
        }
        TextFlowExtension pojo = read(row.getJson());
        return pojoType.isInstance(pojo) ? Optional.of(pojoType.cast(pojo))
                : Optional.empty();
    }

    public void put(HTextFlow htf, TextFlowExtension pojo) {
        TextFlowExtensionFactory<?> f = registry.byPojo(pojo.getClass());
        if (f == null) {
            throw new IllegalArgumentException(
                    "No registered extension factory for "
                            + pojo.getClass().getName());
        }
        String json = write(pojo);
        String search = searchTextOf(f, pojo);
        HTextFlowExtension row = find(htf, f.type());
        if (row == null) {
            HTextFlowExtension created =
                    new HTextFlowExtension(htf, f.type(), json);
            created.setSearchText(search);
            htf.getExtensions().add(created);
        } else {
            row.setJson(json);
            row.setSearchText(search);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends TextFlowExtension> String searchTextOf(
            TextFlowExtensionFactory<?> f, TextFlowExtension pojo) {
        return ((TextFlowExtensionFactory<T>) f).searchText((T) pojo);
    }

    public void remove(HTextFlow htf, String type) {
        htf.getExtensions().removeIf(r -> type.equals(r.getType()));
    }

    private HTextFlowExtension find(HTextFlow htf, String type) {
        for (HTextFlowExtension r : htf.getExtensions()) {
            if (type.equals(r.getType())) {
                return r;
            }
        }
        return null;
    }

    private String write(TextFlowExtension pojo) {
        try {
            return mapper.writerFor(TextFlowExtension.class)
                    .writeValueAsString(pojo);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot serialize text flow extension " + pojo, e);
        }
    }

    private TextFlowExtension read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, TextFlowExtension.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot deserialize text flow extension " + json, e);
        }
    }
}
