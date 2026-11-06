package com.vinland.app.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Records calls and answers with a canned result or error. */
class FakeFunctionClient implements FunctionClient {

    final List<String> calledNames = new ArrayList<>();
    final List<Map<String, Object>> calledData = new ArrayList<>();
    Object nextResult;
    CallException nextError;

    @Override
    public void call(String functionName, Map<String, Object> data, Callback callback) {
        calledNames.add(functionName);
        calledData.add(data);
        if (nextError != null) {
            callback.onFailure(nextError);
        } else {
            callback.onSuccess(nextResult);
        }
    }
}
