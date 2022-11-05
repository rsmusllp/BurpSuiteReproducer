package com.rsm.reproducer;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.ContentType;
import burp.api.montoya.http.message.headers.HttpHeader;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.translate.CharSequenceTranslator;
import org.apache.commons.text.translate.LookupTranslator;
import javax.swing.*;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;

public class PythonRequestBuilder {
    private static final CharSequenceTranslator ESCAPE_QUOTE = new LookupTranslator(
            Map.of("\\", "\\\\", "\"", "\\\"", "\n", "\\n", "\r", "\\r"));
    public static final List<String> SKIP_HEADERS = List.of("host", "cookie");
    private final static String[] PYTHON_ESCAPE = new String[256];
    static {
        for (int i = 0x00; i <= 0xFF; i++) PYTHON_ESCAPE[i] = String.format("\\x%02x", i);
        for (int i = 0x20; i < 0x80; i++) PYTHON_ESCAPE[i] = String.valueOf((char)i);
        PYTHON_ESCAPE['\n'] = "\\n";
        PYTHON_ESCAPE['\r'] = "\\r";
        PYTHON_ESCAPE['\t'] = "\\t";
        PYTHON_ESCAPE['"'] = "\\\"";
        PYTHON_ESCAPE['\\'] = "\\\\";
    }
    private enum BodyType {JSON, DATA}
    // Taken from https://requests.readthedocs.io/en/latest/api/
    public static final List<String> SUPPORTED_METHODS = List.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
    MontoyaApi api;

    public PythonRequestBuilder(MontoyaApi api) {
        this.api = api;
    }

    public StringBuilder build(HttpRequest request) {
        List<ParsedHttpParameter> cookies = request.parameters().stream().filter(p -> p.type() == HttpParameterType.COOKIE).collect(Collectors.toList());
        boolean hasCookies = cookies.size() > 0;
        List<HttpHeader> filteredHeaders = request.headers().subList(1, request.headers().size())
                .stream().filter(e -> !SKIP_HEADERS.contains(e.name().toLowerCase()))
                .collect(Collectors.toList());
        boolean hasHeaders = filteredHeaders.size() > 0;

        if (!(SUPPORTED_METHODS.contains(request.method()))) {
            JOptionPane.showMessageDialog(new JFrame(), "The \"" + StringUtils.abbreviate(request.method(), 16)
                    + "\" method is not supported by PowerShell Invoke-WebRequest.", "Error", JOptionPane.ERROR_MESSAGE);
        }

        String requestsMethodPrefix = "\nrequests.";
        String prefix = "burp_";

        StringBuilder py = new StringBuilder("import requests");
        py.append("\n\n").append(prefix).append("url = \"").append(StringEscapeUtils.builder(ESCAPE_QUOTE).escape(request.url())).append('"');
        if (hasHeaders) processHeaders(prefix, py, filteredHeaders);
        if (hasCookies) processCookies(prefix, py, cookies);

        BodyType bodyType = processBody(prefix, py, request);

        py.append(requestsMethodPrefix);
        py.append(request.method().toLowerCase());
        py.append('(').append(prefix).append("url");
        if (hasHeaders) py.append(", headers=").append(prefix).append("headers");
        if (hasCookies) py.append(", cookies=").append(prefix).append("cookies");
        if (bodyType != null) {
            String kind = bodyType.toString().toLowerCase();
            py.append(", ").append(kind).append('=').append(prefix).append(kind);
        }
        py.append(')');

        return py;
    }

    private static void processCookies(String prefix, StringBuilder py,
                                          List<ParsedHttpParameter> cookies) {
        py.append('\n').append(prefix).append("cookies = {");
        StringJoiner cookiesJoin = new StringJoiner(", ");
        for (ParsedHttpParameter cookie : cookies) {
            String cookieName = StringEscapeUtils.builder(ESCAPE_QUOTE).escape(cookie.name())
                    .toString();
            String cookieValue = StringEscapeUtils.builder(ESCAPE_QUOTE).escape(cookie.value())
                    .toString();

            cookiesJoin.add('"' + cookieName + "\": \"" + cookieValue + '"');
        }
        py.append(cookiesJoin);
        py.append('}');
    }

    private static void processHeaders(String prefix, StringBuilder py, List<HttpHeader> headers) {
        py.append('\n').append(prefix).append("headers = {");
        StringJoiner headersJoin = new StringJoiner(", ");
        for (HttpHeader header : headers) {
            String headerName = StringEscapeUtils.builder(ESCAPE_QUOTE).escape(header.name())
                    .toString();
            String headerValue = StringEscapeUtils.builder(ESCAPE_QUOTE).escape(header.value())
                    .toString();

            headersJoin.add('"' + headerName + "\": \"" + headerValue + '"');
        }
        py.append(headersJoin);
        py.append('}');
    }

    private BodyType processBody(String prefix, StringBuilder py,
                                 HttpRequest request) {
        api.logging().logToOutput("Length: " + request.body().length);
        if (request.body().length == 0) return null;
        py.append('\n').append(prefix);
        ContentType contentType = request.contentType();
        if (contentType == ContentType.JSON) {
            try {
                JsonElement rootNode = new Gson().fromJson(byteSliceToString(request.body()), JsonElement.class);
                py.append("json=");
                escapeJson(rootNode, py);
                return BodyType.JSON;
            } catch (Exception e) {
                e.printStackTrace(api.logging().error());
            }
        }
        py.append("data = ");
        if (contentType == ContentType.URL_ENCODED) {
            List<ParsedHttpParameter> params = request.parameters().stream().filter(p -> p.type() == HttpParameterType.BODY).collect(Collectors.toList());
            StringJoiner paramsJoin = new StringJoiner(", ");
            py.append('{');
            for (ParsedHttpParameter param : params) {
                String paramName = StringEscapeUtils.builder(ESCAPE_QUOTE).escape(param.name())
                        .toString();
                String paramValue = StringEscapeUtils.builder(ESCAPE_QUOTE).escape(param.value())
                        .toString();

                paramsJoin.add('"' + paramName + "\": \"" + paramValue + '"');
            }
            py.append(paramsJoin);
            py.append('}');
        } else {
            escapeBytes(request.body(), py);
        }
        return BodyType.DATA;
    }

    private static final String PYTHON_TRUE = "True", PYTHON_FALSE = "False", PYTHON_NULL = "None";

    private static void escapeJson(JsonElement node, StringBuilder output) {
        if (node.isJsonObject()) {
            String prefix = "{";
            Map<String, JsonElement> tm = new TreeMap(String.CASE_INSENSITIVE_ORDER);
            tm.putAll(node.getAsJsonObject().asMap());
            for (Map.Entry<String, JsonElement> e : tm.entrySet()) {
                output.append(prefix);
                prefix = ", ";
                escapeString(e.getKey(), output);
                output.append(": ");
                escapeJson(e.getValue(), output);
            }
            output.append('}');
        } else if (node.isJsonArray()) {
            output.append('[');
            final Iterator<JsonElement> iter = node.getAsJsonArray().iterator();
            if (iter.hasNext()) {
                escapeJson(iter.next(), output);
                while (iter.hasNext()) {
                    output.append(", ");
                    escapeJson(iter.next(), output);
                }
            }
            output.append(']');
        } else if (node.isJsonPrimitive()) {
            JsonPrimitive nodePrimitive = node.getAsJsonPrimitive();
            if (nodePrimitive.isString()) {
                escapeString(nodePrimitive.getAsString(), output);
            } else if (nodePrimitive.isBoolean()) {
                output.append(nodePrimitive.getAsBoolean() ? PYTHON_TRUE : PYTHON_FALSE);
            } else if (nodePrimitive.isNumber()) {
                output.append(nodePrimitive.getAsNumber());
            }
        } else if (node.isJsonNull()) {
            output.append(PYTHON_NULL);
        }
    }

    private static String byteSliceToString(byte[] input) {
        try {
            return new String(input, "ISO-8859-1");
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("All JVMs must support ISO-8859-1");
        }
    }

    private static void escapeString(String input, StringBuilder output) {
        output.append('"');
        int length = input.length();
        for (int pos = 0; pos < length; pos++) {
            output.append(PYTHON_ESCAPE[input.charAt(pos) & 0xFF]);
        }
        output.append('"');
    }

    private static void escapeBytes(byte[] input, StringBuilder output) {
        output.append('"');
        for (byte b : input) {
            output.append(PYTHON_ESCAPE[b & 0xFF]);
        }
        output.append('"');
    }
}
