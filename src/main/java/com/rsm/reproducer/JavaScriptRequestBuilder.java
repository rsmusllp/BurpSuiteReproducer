package com.rsm.reproducer;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.headers.HttpHeader;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import org.apache.commons.lang3.StringUtils;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class JavaScriptRequestBuilder {
    // Taken from https://developer.mozilla.org/en-US/docs/Glossary/Forbidden_header_name
    public static final List<String> SKIP_HEADERS = List.of("accept-charset", "accept-Encoding", "access-control-request-headers", "access-control-request-method", "connection", "content-length", "cookie", "date", "dnt", "expect", "feature-policy", "host", "keep-alive", "origin", "referer", "te", "trailer", "transfer-encoding", "upgrade", "via");
    public static final List<String> SKIP_HEADERS_PREFIX  = List.of("sec-", "proxy-");
    private final static String[] ESCAPE = new String[256];

    static {
        for (int i = 0x00; i <= 0xFF; i++)
            ESCAPE[i] = String.format("\\x%02x", i);
        for (int i = 0x20; i < 0x80; i++)
            ESCAPE[i] = String.valueOf((char) i);
        ESCAPE['\''] = "\\\'";
        ESCAPE['\\'] = "\\\\";
    }
    private MontoyaApi api;

    public JavaScriptRequestBuilder(MontoyaApi api) {
        this.api = api;
    }

    public StringBuilder build(HttpRequest request) {
        StringBuilder node = new StringBuilder();
        String method = request.method();
        List<ParsedHttpParameter> cookies = request.parameters().stream().filter(p -> p.type() == HttpParameterType.COOKIE).collect(Collectors.toList());
        boolean hasCookies = cookies.size() > 0;
        List<HttpHeader> filteredHeaders = request.headers().subList(1, request.headers().size())
                .stream().filter(e -> !(SKIP_HEADERS.contains(e.name().toLowerCase())) && !StringUtils.startsWithAny(e.name().toLowerCase(), SKIP_HEADERS_PREFIX.toArray(new CharSequence[SKIP_HEADERS_PREFIX.size()])))
                .collect(Collectors.toList());
        boolean hasHeaders = filteredHeaders.size() > 0;
        boolean hasBody = request.body().length > 0;


        if (hasCookies) {
            node.append("// Issue request from ").append(request.url(), 0, StringUtils.ordinalIndexOf(request.url(), "/", 3)).append(" for cookies to be included\n");
            for (ParsedHttpParameter cookie : cookies) {
                node.append("document.cookie = '").append(cookie.name()).append("=").append(cookie.value()).append("; path=/'\n");
            }
            node.append("\n");
        }

        StringJoiner options = new StringJoiner(",\n\t");
        options.add("method: '" + method + "'");
        if (hasCookies) {
            options.add("credentials: 'include'");
        }
        if (hasHeaders) {
            StringBuilder headersList = new StringBuilder();
            headersList.append("headers: {\n\t\t");
            StringJoiner headersJoin = new StringJoiner(",\n\t\t");
            for (HttpHeader header : filteredHeaders) {
                headersJoin.add("'" + header.name() + "': '" + header.value() + "'");
            }
            headersList.append(headersJoin);
            headersList.append("\n\t}");
            options.add(headersList);
        }
        if (hasBody && (!method.equals("GET") || !method.equals("HEAD"))) {
            StringBuilder body = new StringBuilder();
            body.append("body: '");
            body.append(escapeBytes(request.body()));
            body.append("'");
            options.add(body);
        }

        node.append("fetch('").append(request.url()).append("', {\n\t");
        node.append(options);
        node.append("\n});");

        return node;
    }

    private static StringBuilder escapeBytes(byte[] input) {
        StringBuilder output = new StringBuilder();
        for (byte b : input) {
            output.append(ESCAPE[b & 0xFF]);
        }
        return output;
    }
}
