package org.digitalforge.monobuild.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

public class YamlHelper {

    public static final ObjectMapper MAPPER;

    static {

        YAMLFactory factory = new YAMLFactory();
        factory.configure(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR, true);
        factory.configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);
        factory.configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false);

        MAPPER = new ObjectMapper(factory);

    }

}
