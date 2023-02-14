package com.rsm.reproducer;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import org.apache.commons.text.translate.CharSequenceTranslator;
import org.apache.commons.text.translate.LookupTranslator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import javax.swing.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class PowerShellBuilder {
    private static final int IWR_MAXIMUM_REDIRECTION = 0;
    private static final String IWR_BASIC_INVOCATION = "Invoke-WebRequest -Method $method -Uri $URI -MaximumRedirection $maximumRedirection -Headers $headers ";
    // https://docs.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/invoke-webrequest
    public static final List<String> SUPPORTED_METHODS = List.of("DEFAULT", "GET", "HEAD", "POST", "PUT", "DELETE",
            "TRACE", "OPTIONS", "MERGE", "PATCH");
    public static final List<String> SKIP_HEADERS = List.of("connection", "content-length", "cookie");
    // reference used for escaping rules: https://ss64.com/ps/syntax-esc.html
    private static final CharSequenceTranslator ESCAPE_POWERSHELL = new LookupTranslator(
            Map.of("`", "``", "#", "`#", "\"", "`\"", "'", "`'", "$", "`$"));

    private MontoyaApi api;
    private boolean hasBody;
    private boolean hasContentType;
    private boolean hasCookieParams;
    private boolean hasUserAgent;
    private boolean isBase64;
    private boolean isStandard;

    public PowerShellBuilder(MontoyaApi api) {
        this.api = api;
    }

    public StringBuilder build(HttpRequest request) {
        StringBuilder stringBuilder = new StringBuilder();
        String method = StringEscapeUtils.builder(ESCAPE_POWERSHELL).escape(request.method()).toString();
        String URI = StringEscapeUtils.builder(ESCAPE_POWERSHELL).escape(request.url()).toString();

        if (!(SUPPORTED_METHODS.contains(method))) {
            JOptionPane.showMessageDialog(new JFrame(), "The \"" + StringUtils.abbreviate(request.method(), 16)
                    + "\" method is not supported by PowerShell Invoke-WebRequest.", "Error", JOptionPane.ERROR_MESSAGE);
            return new StringBuilder("The request method is not supported.");
        }

        stringBuilder.append("Add-Type -AssemblyName Microsoft.PowerShell.Commands.Utility").append(System.lineSeparator());
        stringBuilder.append("$method = [Microsoft.PowerShell.Commands.WebRequestMethod]::").append("\"").append(method)
                .append("\"").append(System.lineSeparator()).append("$URI = [System.Uri]::new(\"").append(URI).append("\")")
                .append(System.lineSeparator()).append("$maximumRedirection = [System.Int32] ")
                .append(IWR_MAXIMUM_REDIRECTION).append(System.lineSeparator());
        stringBuilder.append(processHeaders(request.headers()));
        stringBuilder.append(processParams(request.parameters()));
        stringBuilder.append(processBody(request));

        processIWR(stringBuilder);

        return stringBuilder;
    }

    private StringBuilder processHeaders(List<HttpHeader> headers) {
        this.hasContentType = false;
        this.hasUserAgent = false;
        StringBuilder stringBuilder = new StringBuilder(
                "$headers = [System.Collections.Generic.Dictionary[string,string]]::new()").append(System.lineSeparator());

        // skip the first header line
        for (HttpHeader header : headers.subList(1, headers.size())) {
            String headerName = StringEscapeUtils.builder(ESCAPE_POWERSHELL).escape(header.name())
                    .toString();
            String headerValue = StringEscapeUtils.builder(ESCAPE_POWERSHELL).escape(header.value())
                    .toString();

            if (!(SKIP_HEADERS.contains(headerName.toLowerCase()))) {
                switch (header.name().toLowerCase()) {
                    case "content-type":
                        this.hasContentType = true;
                        stringBuilder.append("$contentType = [System.String]::new(\"").append(headerValue).append("\")")
                                .append(System.lineSeparator());
                        break;
                    case "user-agent":
                        this.hasUserAgent = true;
                        stringBuilder.append("$userAgent = [System.String]::new(\"").append(headerValue).append("\")")
                                .append(System.lineSeparator());
                        break;
                    default:
                        stringBuilder.append("$headers.Add(\"").append(headerName).append("\", \"").append(headerValue).append("\")")
                                .append(System.lineSeparator());
                        break;
                }
            }
        }

        return stringBuilder;
    }

    private StringBuilder processParams(List<ParsedHttpParameter> parameters) {
        this.hasCookieParams = false;
        boolean isCookieFirstIteration = true;
        StringBuilder stringBuilder = new StringBuilder();

        if (!(parameters.isEmpty())) {
            for (HttpParameter parameter : parameters) {
                String parameterName = StringEscapeUtils.builder(ESCAPE_POWERSHELL).escape(parameter.name())
                        .toString();
                String parameterValue = StringEscapeUtils.builder(ESCAPE_POWERSHELL).escape(parameter.value())
                        .toString();

                if (parameter.type() == HttpParameterType.COOKIE) {
                    if (isCookieFirstIteration) {
                        this.hasCookieParams = true;
                        isCookieFirstIteration = false;
                        stringBuilder.append("$webSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()")
                                .append(System.lineSeparator());
                    }

                    stringBuilder.append("$webSession.Cookies.Add($URI, [System.Net.Cookie]::new(\"").append(parameterName)
                            .append("\", \"").append(parameterValue).append("\"))").append(System.lineSeparator());
                }
            }
        }

        return stringBuilder;
    }

    private StringBuilder processBody(HttpRequest request) {
        this.hasBody = false;
        this.isBase64 = false;
        this.isStandard = false;
        byte[] requestBody = request.body().getBytes();
        StringBuilder stringBuilder = new StringBuilder();

        if (requestBody.length > 0) {
            this.hasBody = true;
            if (!isStandard(requestBody)) {
                this.isBase64 = true;
                String body64 = Base64.getEncoder().encodeToString(requestBody);
                stringBuilder.append("$body64 = [System.String]::new(\"").append(body64).append("\")")
                        .append(System.lineSeparator()).append("$bytes = [System.Convert]::FromBase64String($body64)")
                        .append(System.lineSeparator());
            } else {
                this.isStandard = true;
                String body = StringEscapeUtils.builder(ESCAPE_POWERSHELL)
                        .escape(new String(requestBody, StandardCharsets.UTF_8)).toString();
                stringBuilder.append("$body = [System.String]::new(\"").append(body).append("\")")
                        .append(System.lineSeparator());
            }
        }

        return stringBuilder;
    }

    private StringBuilder processIWR(StringBuilder stringBuilder) {
        stringBuilder.append("$response = (").append(IWR_BASIC_INVOCATION);

        if (this.hasContentType) {
            stringBuilder.append("-ContentType $contentType ");
        }

        if (this.hasUserAgent) {
            stringBuilder.append("-UserAgent $userAgent ");
        }

        if (this.hasCookieParams) {
            stringBuilder.append("-WebSession $webSession ");
        }

        if (this.hasBody && this.isBase64) {
            if (!(stringBuilder.toString().contains("-Body"))) {
                stringBuilder.append("-Body $bytes ");
            } else {
                stringBuilder.deleteCharAt(stringBuilder.lastIndexOf(" ")).append(", $bytes ");
            }
        } else if (this.hasBody && this.isStandard) {
            if (!(stringBuilder.toString().contains("-Body"))) {
                stringBuilder.append("-Body $body ");
            } else {
                stringBuilder.deleteCharAt(stringBuilder.lastIndexOf(" ")).append(", $body ");
            }
        }

        stringBuilder.deleteCharAt(stringBuilder.lastIndexOf(" ")).append(")").append(System.lineSeparator())
                .append("$response");

        return stringBuilder;
    }

    private boolean isStandard(byte[] data) {
        for (byte b : data) {
            if (b < 0x20 || b >= 0x80) {
                return false;
            }
        }
        return true;
    }
}
