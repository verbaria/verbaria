package org.zanata.rest.dto.extensions;

import java.util.List;

public interface ParameterizedExtension {

    List<String> getParamNames();

    List<String> getParamTypes();
}
