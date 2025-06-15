package jLib;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.net.http.*;
import java.nio.charset.StandardCharsets;



public class LLmLib {



    private static final String _modelInfo = """
        {
            "opus": {
                "modelName": "claude-opus-4-20250514",
                "endpoint": "ANTHROPIC/ENDPOINT",
                "apiKey": "ANTHROPIC/API_KEY",
                "vision": true,
                "method_name": "llm_call_anthropic"
            },
            "sonnet": {
                "modelName": "claude-3-7-sonnet-latest",
                //"modelName": "claude-sonnet-4-20250514", // TODO: fails image test
                "endpoint": "ANTHROPIC/ENDPOINT",
                "apiKey": "ANTHROPIC/API_KEY",
                "vision": true,
                "method_name": "llm_call_anthropic"
            },
            "gemini": {
                "modelName": "gemini-2.0-flash",
                "endpoint": "https://generativelanguage.googleapis.com/v1/models",
                "apiKey": "GOOGAI/API_KEY",
                "vision": true,
                "method_name": "llm_call_google"
            },
            "gemini-lite": {
                "modelName": "gemini-2.0-flash-lite",
                "endpoint": "https://generativelanguage.googleapis.com/v1/models",
                "apiKey": "GOOGAI/API_KEY",
                "vision": true,
                "method_name": "llm_call_google"
            },
            "gemini-2.5": {
                "modelName": "gemini-2.5-pro-preview-06-05",
                "endpoint": "https://generativelanguage.googleapis.com/v1beta/models",
                "apiKey": "GOOGAI/API_KEY",
                "vision": true,
                "method_name": "llm_call_google"
            },
            "gpt4o": {
                "modelName": "gpt-4o",
                "endpoint": "OPENAI/LLM_ENDPOINT",
                "apiKey": "OPENAI/API_KEY",
                "vision": true,
                "method_name": "llm_call_openai_compat"
            }
        }
    """;
    String disabledModelInfo = """
            {
                "grok-2": {
                "modelName": "grok-2-1212",
                "endpoint": "GROK/ENDPOINT",
                "apiKey": "GROK/API_KEY",
                "vision": false,
                "method_name": "llm_call_openai_compat"
            },
            "grok-2-vision": {
                "modelName": "grok-2-vision-1212",
                "endpoint": "GROK/ENDPOINT",
                "apiKey": "GROK/API_KEY",
                "vision": true,
                "method_name": "llm_call_openai_compat"
            },
    """;
    private static final Map<String,Object> MODELS;
    static {
        // first, replace every string that looks like "\w+(/\w+)+" with the value of the corresponding credential
        String regex = "(?<=\")\\w+(/\\w+)+(?=\")";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(_modelInfo);
        StringBuffer sb = new StringBuffer();
        while ( matcher.find() ) {
            String key = matcher.group();
            Object valueObj = LibApp.loadCreds().get(key);
            if (valueObj instanceof Jsonable) valueObj = ((Jsonable) valueObj).get();
            String value = valueObj instanceof String ? (String) valueObj : null;
            if (value==null) throw new RuntimeException( "can't find: "+key );
            matcher.appendReplacement(sb, value);
        }
        matcher.appendTail(sb);
        MODELS = JsonDecoder.decodeMap( sb.toString() );
    }




    /**
     * @param extraPayload will be written into the body of the request; possibly overwriting e.g. temperature.
     * @param systemPrompt can be null.
     * @param logFile is typically null; used for internal retries.
     * @param triesAllowed is typically null; used for internal retries.
     * @return The result of the call; text response will be the output if successful or error message if not.
     */
    public Result<String,Exception> llmCallAnthropic(
        List<Object> promptParts,
        String apiKey, String endpoint, String modelName, String systemPrompt,
        Map<String, Object> extraPayload, Integer triesAllowed, File logFile
    ) {
        promptParts = mergeTemplates(promptParts);
        logFile = logLLmCall(null, "PROMPT", Lib.mapOf(
                "promptParts", promptParts,
                "endpoint", endpoint,
                "modelName", modelName,
                "systemPrompt", systemPrompt,
                "extraPayload", extraPayload,
                null
        ), modelName );
        if (triesAllowed == null) triesAllowed = 3;
        try {
            // Build the request body for Anthropic
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("max_tokens", 8192);
            body.put("temperature", 0.0);
            // Build the message content array
            List<Map<String, Object>> messageContent = new ArrayList<>();
            for (Object part : promptParts) {
                if (part instanceof File) {
                    File file = (File) part;
                    String mimeType = LibFile.getMimeType(file);
                    // For non-image and non-PDF files, convert file to text
                    if (!mimeType.startsWith("image/") && !mimeType.endsWith("/pdf")) {
                        String fileText = LibFile.file2string( file );
                        Map<String, Object> textMap = new LinkedHashMap<>();
                        textMap.put("type", "text");
                        textMap.put("text", formatPrompt(fileText));
                        messageContent.add(textMap);
                    } else {
                        // Process file as a binary block
                        byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
                        String base64Data = Base64.getEncoder().encodeToString(fileBytes);
                        Map<String, Object> fileMap = new LinkedHashMap<>();
                        String partType = mimeType.split("/")[0];
                        partType = partType.equals("application") ? "document" : partType;
                        fileMap.put("type", partType);
                        Map<String, Object> sourceMap = new LinkedHashMap<>();
                        sourceMap.put("type", "base64");
                        sourceMap.put("media_type", mimeType);
                        sourceMap.put("data", base64Data);
                        fileMap.put("source", sourceMap);
                        messageContent.add(fileMap);
                    }
                } else {
                    // For non-file parts, treat as text
                    String text = formatPrompt(part);
                    Map<String, Object> textMap = new LinkedHashMap<>();
                    textMap.put("type", "text");
                    textMap.put("text", text);
                    messageContent.add(textMap);
                }
            }
            // Create the messages list required by Anthropic
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMessage = new LinkedHashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", messageContent);
            messages.add(userMessage);
            body.put("messages", messages);
            // Add the system prompt if provided (outside the messages array)
            if (! Lib.isEmpty(systemPrompt) ) body.put("system", systemPrompt);
            // Merge any extra payload data
            if (! Lib.isEmpty(extraPayload) ) body.putAll(extraPayload);
            // Log the request
            logLLmCall(logFile, "REQUEST", body, null);
            Lib.log("calling anthropic " + modelName + "...");
            long startTime = System.currentTimeMillis();
            // Prepare headers specific to Anthropic API
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("x-api-key", apiKey);
            headers.put("anthropic-version", "2023-06-01");
            // Make the API call
            Result<HttpRes,HttpRes> httpRes = httpRequest(endpoint,headers,body,"POST");
            long elapsedTime = System.currentTimeMillis() - startTime;
            Lib.log("response time for anthropic " + modelName + ": " + elapsedTime + "ms");
            { // Log extra info.
                List<String> attachedFiles = new ArrayList<>();
                for (Object part : promptParts) {
                    if (part instanceof File f) attachedFiles.add(f.getName());
                }
                Map<String, Object> extraLog = new LinkedHashMap<>();
                extraLog.put("kwargs", Lib.mapOf("endpoint", endpoint, "modelName", modelName,
                    "systemPrompt", systemPrompt, "extraPayload", extraPayload));
                extraLog.put("url", endpoint);
                extraLog.put("attached_files", JsonEncoder.encode(attachedFiles));
                extraLog.put("elapsed_time", elapsedTime);
                logLLmCall(logFile, "EXTRA", extraLog, null);
            }

            if (!httpRes.isOk()) throw new Exception( httpRes.err().body );
            String responseString = httpRes.ok().body;
            Map<String, Object> responseObj = JsonDecoder.decodeMap(responseString);
            logLLmCall(logFile, "RESPONSE", responseObj, null);
            // Simplified text response extraction using Lib.get
            StringBuilder textResponse = new StringBuilder();
            Object contentObj = Jsonable.get(responseObj, "content");
            List<?> contentList = contentObj instanceof List ? (List<?>) contentObj : null;
            if (contentList != null) {
                for (Object item : contentList) {
                    Object textObj = Jsonable.get(item, "text");
                    String text = textObj instanceof String ? (String) textObj : null;
                    if (text==null) continue;
                    if (! textResponse.isEmpty() ) textResponse.append("\n");
                    textResponse.append(text);
                }
            }
            if ( Lib.isEmpty(textResponse) ) throw new Exception("No content in response");
            return Result.<String,Exception>ok(textResponse.toString()).setLogFile(logFile);
        } catch (Exception e) {
            logLLmCall(logFile, "ERROR", e.getMessage(), null);
            if (triesAllowed > 0) {
                Lib.log("retrying " + modelName + "...");
                Map<String, Object> copyExtraPayload = extraPayload != null ? new LinkedHashMap<>(extraPayload) : null;
                return llmCallAnthropic(
                    promptParts, apiKey, endpoint, modelName, systemPrompt, copyExtraPayload, triesAllowed - 1, logFile
                );
            }
            return Result.<String,Exception>err(e).setLogFile(logFile);
        } finally {
            if (logFile != null) logLLmCall(logFile, null, null, null);
        }
    }
    @SuppressWarnings("unused")
    private static boolean llmCallAnthropic_TEST_(boolean findLineNumber) throws IOException {
        if (findLineNumber) throw new RuntimeException();
        String modelKey = "sonnet";
        @SuppressWarnings("unchecked")
        Map<String, Object> modelInfo = (Map<String, Object>) MODELS.get(modelKey);
        File imageFile = new File("./datafiles/files4testing/red_dot.png");
        Lib.asrt(imageFile.exists(), "Test image file must exist");
        Result<String,Exception> res = newInstance().llmCallAnthropic(
            List.of( "What color is this image?", imageFile ),
            (String) modelInfo.get("apiKey"),
            (String) modelInfo.get("endpoint"),
            (String) modelInfo.get("modelName"),
            "Your caps lock is broken and you are also angry, so you can answer ONLY IN UPPER CASE!",
            null, null, null
        );
        Lib.asrt(res.isOk(), "Call should succeed");
        String upperResponse = res.ok().toUpperCase();
        Lib.asrt(
            upperResponse.contains("RED") || upperResponse.contains("ORANGE"),
            "Response should mention RED or ORANGE in uppercase"
        );
        return true;
    }



    /**
     * Lists available Anthropic models by making a request to the Anthropic API.
     * Uses the Anthropic API key and endpoint stored in credentials.
     * @return A Result containing a list of available Anthropic model names
     */
    public Result< List<String>, Exception > listAntropicModels() {
        Object apiKeyObj = LibApp.loadCreds().get("ANTHROPIC/API_KEY");
        if (apiKeyObj instanceof Jsonable) apiKeyObj = ((Jsonable) apiKeyObj).get();
        String apiKey = apiKeyObj instanceof String ? (String) apiKeyObj : null;
        Object endpointObj = LibApp.loadCreds().get("ANTHROPIC/ENDPOINT");
        if (endpointObj instanceof Jsonable) endpointObj = ((Jsonable) endpointObj).get();
        String endpoint = endpointObj instanceof String ? (String) endpointObj : null;
        String url = Lib.normalizePath(endpoint+"/../models");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", "2023-06-01");
        headers.put("Content-Type", "application/json");
        List<String> models = new ArrayList<>();
        try {
            Result<HttpRes,HttpRes> httpRes = httpRequest(url, headers, null, "GET");
            if (! httpRes.isOk() ) throw new Exception( httpRes.err().body );
            Map<String,Object> responseObj = JsonDecoder.decodeMap( httpRes.ok().body );
            Object modelsObj = Jsonable.get(responseObj, "data");
            List<?> modelsList = modelsObj instanceof List ? (List<?>) modelsObj : null;
            if (modelsList==null) modelsList = List.of();
            for (Object model : modelsList) {
                if (model instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String,Object> modelMap = (Map<String,Object>) model;
                    String modelName = (String) modelMap.get("id");
                    if (modelName != null) models.add(modelName);
                }
            }
            return Result.ok(models);
        } catch (Exception e) {
            Lib.log("Failed to retrieve models: " + e.getMessage());
            return Result.err(e);
        }
    }
    @SuppressWarnings("unused")
    private static boolean listAntropicModels_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        Result< List<String>, Exception > res = newInstance().listAntropicModels();
        Lib.asrt(res.isOk(), "Failed to retrieve models");
        Lib.asrt(!res.ok().isEmpty(), "No models returned");
        //Lib.log(Lib.mapOf( "anthropic_models", res.ok() ));
        return true;
    }



    private static final String YES_OR_NO_SCHEMA_STRING = """
        {
            "type": "object",
            "properties": { "answer":{ "type":"string", "enum":["yes","no"] } },
            "required": ["answer"]
        }
    """;



    private String preferredModelKey = "gemini";
    private String preferredJsonModelKey = "gemini-lite";



    public static LLmLib newInstance() {
        return new CachedLLmLib();
    }



    @SuppressWarnings("unused")
    private static boolean everyModel_TEST_( boolean findLineNumber ) throws IOException {
        if (findLineNumber) throw new RuntimeException();
        // set preferred model to each model in turn, and call llmCall with a simple prompt
        for (String modelKey : MODELS.keySet()) {
            LLmLib llmApi = newInstance();
            llmApi.preferredModelKey = modelKey;
            Result<String,Exception> res = llmApi.llmCall(
                List.of( "What is the capital of France?" ),
                null, null, null, null, null
            );
            if (! res.isOk() ) {
                Lib.log( "Failed to call model: "+modelKey+" because: "+res.err().getMessage() );
            }
            Lib.asrt(res.isOk(), "Result should be successful");
            Lib.asrt( res.ok().toUpperCase().indexOf("PARIS") >= 0, "Result should contain 'PARIS'" );
        }
        return true;
    }



    /**
     * Wherever there is a File followed by a Map, merge the Map into the File,
     * then if it finds links to local files in the merged markdown text, it splits the markdown into parts,
     * with the local files as their own parts, usually as a binary, e.g. an image file.
     * @param promptParts
     * @return a new list of prompt parts
     */
    public List<Object> mergeTemplates( List<Object> promptParts ) {
        List<Object> newParts = new ArrayList<>();
        for (int i=0; i<promptParts.size(); i++) {
            Object part = promptParts.get(i);
            if (
                ( part instanceof File f ) &&
                ( (i+1) < promptParts.size() ) &&
                ( promptParts.get(i+1) instanceof Map m )
            ) {
                @SuppressWarnings("unchecked")
                List<Object> parts = templateToPromptParts(f, (Map<String,Object>)m);
                newParts.addAll(parts);
                i++;
            } else { newParts.add(part); }
        }
        return Collections.unmodifiableList(newParts);
    }
    @SuppressWarnings("unused")
    private static boolean mergeTemplates_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        File templateFile = new File("./datafiles/files4testing/prompt_template.md");
        Map<String, Object> vars = Map.of(
            "slow_person_name", "John",
            "middle_person_name", "Mark",
            "fast_person_name", "Jane"
        );
        LLmLib llmLib = newInstance();
        List<Object> parts = llmLib.mergeTemplates( List.of(templateFile,vars) );
        Lib.asrt(parts.size() == 5, "Expected 5 parts but got " + parts.size());
        Lib.asrt(parts.get(1) instanceof File, "Expected parts[1] to be a File");
        // running the parts through mergeTemplates again should not change the result
        Lib.asrt( parts.equals(llmLib.mergeTemplates(parts)), "mergeTemplates should not change the result" );
        // make sure the template makes sense to an LLM
        Result< Pair<Boolean,String>, Exception > res = llmLib.llmYesOrNo(
            parts, null, null, null
        );
        Lib.asrt(res.isOk(), "Result should be successful");
        Lib.asrt(res.ok() != null, "Output should not be null");
        return true;
    }



    /**
     * Lists available Google Gemini models by making a request to the Google API.
     * Uses the Google API key stored in credentials.
     *
     * @return A list of available Google model names
     */
    public Result<List<String>,Exception> listGoogleModels() {
        Object apiKeyObj = LibApp.loadCreds().get("GOOGAI/API_KEY");
        if (apiKeyObj instanceof Jsonable) apiKeyObj = ((Jsonable) apiKeyObj).get();
        String apiKey = apiKeyObj instanceof String ? (String) apiKeyObj : null;
        String url = "https://generativelanguage.googleapis.com/v1/models";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-goog-api-key", apiKey);
        headers.put("Content-Type", "application/json");
        List<String> models = new ArrayList<>();
        try {
            Result<HttpRes,HttpRes> httpRes = httpRequest(url, headers, null, "GET");
            if (!httpRes.isOk()) throw new Exception(httpRes.err().body );
            Map<String, Object> responseObj = JsonDecoder.decodeMap(httpRes.ok().body );
            Object modelsObj = Jsonable.get(responseObj, "models");
            List<?> modelsList = modelsObj instanceof List ? (List<?>) modelsObj : null;
            if (modelsList != null) {
                for (Object model : modelsList) {
                    if (model instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> modelMap = (Map<String, Object>) model;
                        String modelName = (String) modelMap.get("name");
                        if (modelName != null) {
                            models.add(modelName);
                        }
                    }
                }
            }
            return Result.ok(models);
        } catch (Exception e) {
            Lib.log("Failed to retrieve models: " + e.getMessage());
            return Result.err(e);
        }
    }
    @SuppressWarnings("unused")
    private static boolean listGoogleModels_TEST_(boolean findLineNumber) {
        if (findLineNumber) throw new RuntimeException();
        Result< List<String>, Exception > res = newInstance().listGoogleModels();
        Lib.asrt(res.isOk(), "Failed to retrieve models");
        Lib.asrt(!res.ok().isEmpty(), "No models returned");
        //Lib.log(Lib.mapOf( "google_models", res.ok() ));
        return true;
    }



    /**
     * Lists available OpenAI models by making a request to the OpenAI API.
     * Uses the OpenAI API key stored in credentials.
     * @return A Result containing a list of available OpenAI model names
     */
    public Result< List<String>, Exception > listOpenAiModels() {
        Object apiKeyObj = LibApp.loadCreds().get("OPENAI/API_KEY");
        if (apiKeyObj instanceof Jsonable) apiKeyObj = ((Jsonable) apiKeyObj).get();
        String apiKey = apiKeyObj instanceof String ? (String) apiKeyObj : null;
        String url = "https://api.openai.com/v1/models";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        List<String> models = new ArrayList<>();
        try {
            Result<HttpRes,HttpRes> httpRes = httpRequest(url, headers, null, "GET");
            if (!httpRes.isOk()) return Result.err(new Exception( httpRes.err().body ));
            Map<String, Object> responseObj = JsonDecoder.decodeMap( httpRes.ok().body );
            Object modelsObj = Jsonable.get(responseObj, "data");
            List<?> modelsList = modelsObj instanceof List ? (List<?>) modelsObj : null;
            if (modelsList != null) {
                for (Object model : modelsList) {
                    if (model instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> modelMap = (Map<String, Object>) model;
                        String modelName = (String) modelMap.get("id");
                        if (modelName != null) {
                            models.add(modelName);
                        }
                    }
                }
            }
            return Result.ok(models);
        } catch (Exception e) {
            Lib.log("Failed to retrieve models: " + e.getMessage());
            return Result.err(e);
        }
    }
    @SuppressWarnings("unused")
    private static boolean listOpenAiModels_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        Result< List<String>, Exception > res = newInstance().listOpenAiModels();
        //Lib.log(Lib.mapOf("openAi_models", res.ok() ));
        Lib.asrt(res.isOk(), "Failed to retrieve models");
        Lib.asrt(!res.ok().isEmpty(), "No models returned");
        return true;
    }



    /**
     * Extracts the last code block from a text response.
     * Attempts to find code blocks with language identifiers first.
     * If none found, checks if the text is a JSON array or object.
     * Otherwise, returns the entire text as is.
     *
     * @param textResponse The text response containing potential code blocks
     * @return A Result containing the extracted code if successful
     */
    public static String findCodeInResponse(String textResponse) {
        Pattern pattern = Pattern.compile(
            "```(?:html|python|javascript|json|markdown|xml|yaml)?([^`]+)```",
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(textResponse);
        String lastMatch = null;
        while (matcher.find()) lastMatch = matcher.group(1).trim();
        if (lastMatch != null) return lastMatch;
        String code = textResponse.replaceAll("^\\s*`*|`*\\s*$", "");
        if ((code.startsWith("[") && code.endsWith("]")) ||
            (code.startsWith("{") && code.endsWith("}"))) {
            return code;
        }
        return textResponse;
    }
    @SuppressWarnings("unused")
    private static boolean findCodeInResponse_TEST_(boolean findLineNumber) {
        if (findLineNumber) throw new RuntimeException();
        String textResponse = """
            Here is some code:
            ```python
            def foo(x):
                return x**2
            ```
        """;
        String code = findCodeInResponse(textResponse);
        Lib.asrt( Lib.nw(code).equals("def foo(x): return x**2"), "Expecting def foo" );
        textResponse = " ['ok'] ";
        code = findCodeInResponse(textResponse);
        Lib.asrt( code.equals("['ok']"), "JSON array should be properly extracted" );
        return true;
    }



    /**
     * Call one of the Gemini models (Google LLM).
     * Builds the HTTP POST body with a "contents" array containing a single "user" message with "parts",
     * and a "generationConfig" specifying temperature and topP.
     *
     * @param promptParts A list of prompt parts (Strings or Files, or other objects that can be formatted)
     * @param apiKey Your Google API key.
     * @param endpoint Not used for Google; provided for compatibility.
     * @param modelName The name of the Gemini model (e.g., "gemini-2.0-flash").
     * @param systemPrompt A system prompt to be prepended if provided.
     * @param extraPayload Extra payload to merge into the JSON body.
     * @param triesAllowed How many retries remain.
     * @param logFile Log file to record the process.
     * @return A Result containing the text response if successful, or an error message.
     */
    public Result<String,Exception> llmCallGoogle(
        List<Object> promptParts,
        String apiKey, String endpoint, String modelName, String systemPrompt,
        Map<String, Object> extraPayload, Integer triesAllowed, File logFile
    ) {
        promptParts = mergeTemplates(promptParts);
        logFile = logLLmCall( logFile, "PROMPT", Lib.mapOf(
            "promptParts", promptParts,
            "endpoint", endpoint,
            "modelName", modelName,
            "systemPrompt", systemPrompt,
            "extraPayload", extraPayload,
            null
        ), modelName );
        if (triesAllowed == null) triesAllowed = 3;
        try {
            // Build the fundamental body.
            Map<String, Object> body = new LinkedHashMap<>();
            // Create the "contents" array with a single user message.
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> userContent = new LinkedHashMap<>();
            userContent.put("role", "user");
            List<Map<String, Object>> partsList = new ArrayList<>();
            // Process each prompt part.
            for (Object part : promptParts) {
                // For non-string types, call formatPrompt.
                if (!(part instanceof String) && !(part instanceof File)) {
                    part = formatPrompt(part);
                }
                if (part instanceof String) {
                    Map<String, Object> textPart = new LinkedHashMap<>();
                    textPart.put("text", part);
                    partsList.add(textPart);
                } else if (part instanceof File) {
                    File file = (File) part;
                    byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
                    String base64Data = Base64.getEncoder().encodeToString(fileBytes);
                    String mimeType = LibFile.getMimeType(file);
                    Map<String, Object> inlineData = new LinkedHashMap<>();
                    inlineData.put("mimeType", mimeType);
                    inlineData.put("data", base64Data);
                    Map<String, Object> filePart = new LinkedHashMap<>();
                    filePart.put("inlineData", inlineData);
                    partsList.add(filePart);
                }
            }
            // Prepend system prompt if provided; or if first part is a file.
            if (partsList.get(0).get("inlineData") != null) {
                if ( Lib.isEmpty(systemPrompt) ) {
                    systemPrompt = Lib.nw("""
                        Please examine the following content and then respond as instructed after the content.
                    """);
                }
            }
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                Map<String, Object> systemPart = new LinkedHashMap<>();
                systemPart.put("text", systemPrompt);
                partsList.add(0, systemPart);
            }
            userContent.put("parts", partsList);
            contents.add(userContent);
            body.put("contents", contents);
            // Add the generationConfig.
            Map<String, Object> generationConfig = new LinkedHashMap<>();
            generationConfig.put("temperature", 0.0);
            generationConfig.put("topP", 1.0);
            body.put("generationConfig", generationConfig);
            // Merge any extra payload data.
            if (!Lib.isEmpty(extraPayload)) {
                body.putAll(extraPayload);
            }
            // Log the request.
            logFile = logLLmCall(logFile, "REQUEST", body,null);
            Lib.log("calling google " + modelName + "...");
            long startTime = System.currentTimeMillis();
            // Build the URL as per the Python code.
            String url = endpoint +"/"+ modelName + ":generateContent";
            // Prepare headers: Content-Type and x-goog-api-key.
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("x-goog-api-key", apiKey);
            // Make the API call.
            Result<HttpRes,HttpRes> httpRes = httpRequest(url, headers, body, "POST");
            long elapsedTime = System.currentTimeMillis() - startTime;
            Lib.log("response time for " + modelName + ": " + elapsedTime + "ms");
            if (!httpRes.isOk()) throw new Exception(httpRes.err().body);
            String responseString = httpRes.ok().body;
            Map<String, Object> responseObj = JsonDecoder.decodeMap(responseString);
            { // Log extra info.
                List<String> attachedFiles = new ArrayList<>();
                for (Object part : promptParts) {
                    if (part instanceof File f) attachedFiles.add( f.getName() );
                }
                Map<String, Object> extraLog = new LinkedHashMap<>();
                extraLog.put("kwargs", Lib.mapOf("endpoint", endpoint, "modelName", modelName, "systemPrompt", systemPrompt, "extraPayload", extraPayload));
                extraLog.put("url", url);
                extraLog.put("attached_files", JsonEncoder.encode(attachedFiles) );
                extraLog.put("elapsed_time", elapsedTime);
                logLLmCall(logFile, "EXTRA", extraLog, null);
            }
            logLLmCall(logFile, "RESPONSE", responseObj, null);
            // Extract the response text.
            StringBuilder resultText = new StringBuilder();
            Object parts = Jsonable.get( responseObj, "candidates/0/content/parts" );
            List<?> lst = parts instanceof List ? (List<?>) parts : null;
            if (lst != null) {
                for (Object part : lst) {
                    if (part instanceof Map partMap) {
                        Object text = partMap.get("text");
                        if (text instanceof String) {
                            if (! resultText.isEmpty() ) resultText.append("\n");
                            resultText.append(text);
                        }
                    }
                }
            }
            if ( Lib.isEmpty(resultText) ) throw new Exception("No content in response");
            return Result.<String,Exception>ok(resultText.toString()).setLogFile(logFile);
        } catch(Exception e) {
            logLLmCall(logFile, "ERROR", e.getMessage(), null);
            if (triesAllowed > 0) {
                Lib.log("retrying " + modelName + "...");
                Map<String, Object> copyExtraPayload = extraPayload != null ? new LinkedHashMap<>(extraPayload) : null;
                return llmCallGoogle(promptParts, apiKey, endpoint, modelName, systemPrompt, copyExtraPayload, triesAllowed - 1, logFile);
            }
            return Result.<String,Exception>err(e).setLogFile(logFile);
        } finally {
            if (logFile != null) logLLmCall(logFile, null, null, null);
        }
    }
    @SuppressWarnings("unused")
    private static boolean llmCallGoogle_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        String modelKey = "gemini";
        @SuppressWarnings("unchecked")
        Map<String, Object> modelInfo = (Map<String, Object>) MODELS.get(modelKey);
        File imageFile = new File("./datafiles/files4testing/red_dot.png");
        Lib.asrt(imageFile.exists(), "Test image file must exist");
        Result<String,Exception> res = newInstance().llmCallGoogle(
            List.of( imageFile, "What color is this image?"  ),
            (String) modelInfo.get("apiKey"),
            (String) modelInfo.get("endpoint"),
            (String) modelInfo.get("modelName"),
            null, //"Your caps lock is broken and you are also angry, so you can answer ONLY IN UPPER CASE!",
            null, null, null
        );
        Lib.asrt(res.isOk(), "Call should succeed");
        String upperResponse = res.ok().toUpperCase();
        Lib.asrt(
            upperResponse.contains("RED") || upperResponse.contains("ORANGE"),
            "Response should mention RED or ORANGE in uppercase"
        );
        return true;
    }



    private static List<Object> templateToPromptParts( File templateFile, Map<String,Object> vars) {
        if ( templateFile.length() <= 0 ) {
            Lib.logException( "template file is empty or doesn't exist: "+templateFile );
            return List.of();
        }
        String promptString = null;
        try {
            promptString = Lib.evalTemplate(templateFile, vars);
        } catch (IOException e) { throw new RuntimeException(e); }
        String logFilespec = LibFile.backupFilespec( "./log/"+templateFile.getName() );
        File logFile = new File(logFilespec);
        logFile.delete();
        Lib.append2file( logFile, promptString );
        // Find any local files referenced in the template
        String regex = "\\[([^\\]]*)\\]\\((?:(https?|file)://)?([^)]+)\\)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(promptString);
        List<Object> promptParts = new ArrayList<>();
        int lastMatchEnd = 0;
        String afterLink = "";
        while ( matcher.find() ) {
            String altText = matcher.group(1);
            String protocol = matcher.group(2);
            String path = matcher.group(3);
            if (protocol == null) protocol = "";
            protocol = protocol.toLowerCase();
            if (! List.of("","file").contains(protocol) ) continue;
            // Resolve path relative to the markdown file's directory
            File linkFile = new File(templateFile.getParentFile(), path);
            if (! linkFile.isFile() ) { // try again, not relative to template file
                linkFile = new File(path);
                if (! linkFile.isFile() ) continue;
            }
            if ( Lib.isEmpty(altText) ) altText = linkFile.getName();
            String textBeforeLink = promptString.substring(lastMatchEnd, matcher.start());
            textBeforeLink += "[The following is: " + altText + "](";
            textBeforeLink = afterLink+textBeforeLink;
            if (! Lib.isEmpty(textBeforeLink) ) promptParts.add(textBeforeLink);
            promptParts.add(linkFile);
            afterLink = ")<!-- The above was: "+ LibString.xmlSafeText(altText) +" -->";
            lastMatchEnd = matcher.end();
        }
        String lastPart = afterLink+promptString.substring(lastMatchEnd);
        if (! Lib.isEmpty(lastPart) ) promptParts.add(lastPart);
        return promptParts;
    }
    @SuppressWarnings("unused")
    private static boolean templateToPromptParts_TEST_( boolean findLineNumber ) throws IOException {
        if (findLineNumber) throw new RuntimeException();
        File templateFile = new File("./datafiles/files4testing/prompt_template.md");
        Map<String,Object> vars = Map.of(
            "slow_person_name", "John",
            "middle_person_name", "Mark",
            "fast_person_name", "Jane"
        );
        List<Object> promptParts = templateToPromptParts(templateFile, vars);
        Lib.asrt( promptParts.size()==5, "promptParts size should be 5" );
        Lib.asrt( promptParts.get(1) instanceof File, "promptParts[1] should be a File" );
        Lib.asrt( promptParts.get(2) instanceof String, "promptParts[2] should be a String" );
        Lib.asrt( promptParts.get(3) instanceof File, "promptParts[3] should be a File" );
        return true;
    }



    /**
     * Validates a JSON string against a JSON schema.
     * @deprecated Use JsonSchema.validateJson instead
     * @param schemaString The JSON schema as a string
     * @param jsonString The JSON string to validate
     * @return A list of error messages if validation fails, or an empty list if validation succeeds
     */
    public static List<String> validateJson(String schemaString, String jsonString) {
        return JsonSchema.validateJson(schemaString, jsonString);
    }
    @SuppressWarnings("unused")
    private static boolean validateJson_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        String schemaJson = YES_OR_NO_SCHEMA_STRING;
        String inputJson = JsonEncoder.encode( Map.of("answer","yEs") );
        List<String> errorMessages = validateJson(schemaJson,inputJson);
        Lib.asrt(! errorMessages.isEmpty(), "JSON has an error" );
        // try with perfect json
        inputJson = JsonEncoder.encode( Map.of("answer","no") );
        errorMessages = validateJson(schemaJson,inputJson);
        Lib.asrt( errorMessages.isEmpty(), "JSON has no errors" );
        // try it with bonkers json
        inputJson = ":WTF???!";
        errorMessages = validateJson(schemaJson,inputJson);
        Lib.asrt(! errorMessages.isEmpty(), "JSON has an error" );
        return true;
    }



    /**
     * @Returns a result that -- if successful -- is wraps another Result that is true if the answer is 'yes'.
     * The output of the output is a human-language answer.
     */
    public Result< Pair<Boolean,String>, Exception > llmYesOrNo(
        List<Object> promptParts,
        String modelKey,
        String systemPrompt,
        Map<String, Object> extraPayload
    ) {
        List<String> jsonExamples = List.of("{\"answer\":\"yes\"}", "{\"answer\":\"no\"}");
        Result<String,Exception> originalResult = llmCall(
            promptParts, modelKey, systemPrompt, extraPayload, null, null
        );
        String originalText = originalResult.ok();
        if (! originalResult.isOk() ) return Result.err( originalResult.err() );
        Result<String,Exception> validResult = enforceSchema(
            originalResult.ok(), YES_OR_NO_SCHEMA_STRING, jsonExamples, 2
        );
        if (! validResult.isOk() ) return Result.err( validResult.err() );
        Map<String,String> decodedAnswer = JsonDecoder.decodeMap( validResult.ok() );
        boolean isYes = decodedAnswer.get("answer").toString().equalsIgnoreCase("yes");
        return Result.ok( new Pair<>(isYes,originalText) );
    }
    public Result< Pair<Boolean,String>, Exception > llmYesOrNo( List<Object> promptParts ) {
        return llmYesOrNo(promptParts,null,null,null);
    }
    @SuppressWarnings("unused")
    private static boolean llmYesOrNo_TEST_( boolean findLineNumber ) {
        if (findLineNumber) throw new RuntimeException();
        List<Object> promptParts = List.of( Lib.nw("""
            Would most normal jellyfish be able to survive comfortably on the surface of the sun?
        """) );
        Result< Pair<Boolean,String>, Exception > result = newInstance().llmYesOrNo(promptParts, null, null, null);
        Lib.asrt( result.isOk() && !result.ok().a );
        return true;
    }



    public Result<String,Exception> llmCall( List<Object> promptParts ) {
        return llmCall(promptParts,null,null,null,null,null);
    }
    /**
     * Makes a call to a language model with additional parameters.
     *
     * @param promptParts A list of prompt parts (can include text, files, etc.)
     * @param modelKey The key identifying which model to use, or null to use preferred model
     * @param systemPrompt Optional system prompt
     * @param extraPayload Optional additional parameters to include in the request
     * @param jsonSchema Optional JSON schema to enforce the response format
     * @param jsonExamples Optional examples of valid JSON responses
     * @return A Result containing the response text if successful
     */
    public Result<String, Exception> llmCall(
        List<Object> promptParts,
        String modelKey,
        String systemPrompt,
        Map<String, Object> extraPayload,
        String jsonSchema, List<String> jsonExamples
    ) {
        if (modelKey == null) modelKey = this.preferredModelKey;
        @SuppressWarnings("unchecked")
        Map<String, Object> modelInfo = (Map<String, Object>) MODELS.get(modelKey);
        if (modelInfo == null) {
            return Result.err(new Exception("Unknown model key: " + modelKey));
        }
        String methodName = (String) modelInfo.get("method_name");
        String apiKey = (String) modelInfo.get("apiKey");
        String endpoint = (String) modelInfo.get("endpoint");
        String modelName = (String) modelInfo.get("modelName");
        Result<String, Exception> result;
        try {
            switch (methodName) {
                case "llm_call_google":
                    result = llmCallGoogle(
                        promptParts, apiKey, endpoint, modelName, systemPrompt, extraPayload, null, null
                    );
                    break;
                case "llm_call_anthropic":
                    result = llmCallAnthropic(
                        promptParts, apiKey, endpoint, modelName, systemPrompt, extraPayload,
                        null, null
                    );
                    break;
                case "llm_call_openai_compat":
                    result = llmCallOpenAiCompat(
                        promptParts, apiKey, endpoint, modelName, systemPrompt, extraPayload,
                        null, null
                    );
                    break;
                default:
                    return Result.err(new Exception("Unknown method name: " + methodName));
            }
            if (!result.isOk()) {
                return Result.err(new Exception(result.err()));
            }
            if (jsonSchema != null) {
                result = enforceSchema(result.ok(), jsonSchema, jsonExamples, 2);
            }
            return result;
        } catch (Exception e) {
            return Result.err(e);
        }
    }
    @SuppressWarnings("unused")
    private static boolean llmCall_TEST_(boolean findLineNumber) {
        if (findLineNumber) throw new RuntimeException();
        LLmLib llmLib = newInstance();
        Result<String, Exception> textResult = llmLib.llmCall(
            List.of("What is the capital of France?"), "gemini-lite", null, null, null, null
        );
        Lib.asrt(textResult.isOk(), "Call should succeed");
        Lib.asrt(textResult.ok().toLowerCase().contains("paris"), "Response should mention Paris");

        File imageFile = new File("./datafiles/files4testing/red_dot.png");
        Lib.asrt(imageFile.exists(), "Test image file must exist");
        Result<String, Exception> imageResult = llmLib.llmCall(
            List.of("What color is this image?", imageFile), null,
            "Your caps lock is broken and you are also angry, so you can answer ONLY IN UPPER CASE!",
            null, null, null
        );
        Lib.asrt(imageResult.isOk(), "Image call should succeed");
        String upperResponse = imageResult.ok().toUpperCase();
        Lib.asrt(
            upperResponse.contains("RED") || upperResponse.contains("ORANGE"),
            "Response should mention RED or ORANGE in uppercase"
        );
        return true;
    }



    /**
     * @returns a Result that -- if successful -- is a JSON string that matches the given schema.
     */
    public Result<String, Exception> enforceSchema(
        String text, String jsonSchema, List<String> jsonExamples, Integer triesAllowed
    ) {
        text = findCodeInResponse(text);
        triesAllowed = (triesAllowed == null ? 2 : triesAllowed);
        if (jsonExamples != null) {
            for (String example : jsonExamples) {
                List<String> errors = JsonSchema.validateJson(jsonSchema, example);
                if (!errors.isEmpty()) {
                    Lib.log("Example JSON does not match schema: " + errors.get(0) +"\nSCHEMA:"+ jsonSchema +"\nEXAMPLE:"+ example);
                    return Result.err(new Exception("Example JSON does not match schema: " + errors.get(0)));
                }
            }
        }
        // Special case for YES_OR_NO_SCHEMA
        String lowerText = text.trim().toLowerCase();
        if (YES_OR_NO_SCHEMA_STRING.equals(jsonSchema)) {
            if (lowerText.matches("yes\\b.*")) {
                return Result.ok("{\"answer\":\"yes\"}");
            } else if (lowerText.matches("no\\b.*")) {
                return Result.ok("{\"answer\":\"no\"}");
            }
        }
        // If text is already formatted as JSON, just validate it
        List<String> errorMessages = JsonSchema.validateJson(jsonSchema,text);
        if (errorMessages.isEmpty()) return Result.ok(text);

        // Initialize the prompt parts
        List<Object> promptParts = new ArrayList<>();
        promptParts.add(String.format("Please use this json schema, ```%s```, to express the following:", jsonSchema));
        promptParts.add(text);
        promptParts.add(Lib.nw("""
            If there are several ways to express this as json, choose the simplest correct one.
            Do not include any extra information in the json; follow the schema exactly.
            Do not include the schema in the response; just the json that the schema describes.
        """));
        if (jsonExamples != null) {
            for (String example : jsonExamples) {
                promptParts.add(String.format("Here is an example of a valid result: ```%s```", example));
            }
        }
        String systemPrompt = "You output json, with little or no extra information.";
        String possibleJson = null;
        String lastError = null;
        // Attempt to generate valid JSON with retries
        while (triesAllowed >= 0) {
            if (lastError != null) {
                Lib.log("retrying transform to json...");
                promptParts.add(String.format(
                    "A previous attempt to convert to json failed with error: %s.", lastError
                ));
                promptParts.add(String.format("The incorrect json was: ```%s```", possibleJson));
                promptParts.add("Please retry and provide the correct json.");
            }
            Result<String,Exception> res = llmCall(
                promptParts, preferredJsonModelKey, systemPrompt, null, null, null
            );
            if (!res.isOk()) return res;
            possibleJson = findCodeInResponse(res.ok() );
            // weirdly, sometimes LLMs escape underscores
            errorMessages = validateJson(jsonSchema, possibleJson);
            if ( (!errorMessages.isEmpty()) && possibleJson.contains("\\_")) {
                possibleJson = possibleJson.replace("\\_", "_");
                errorMessages = validateJson(jsonSchema, possibleJson);
            }
            // Return if valid
            if ( errorMessages.isEmpty() ) return Result.ok(possibleJson);
            // Prepare for next retry
            lastError = errorMessages.get(0);
            triesAllowed--;
        }
        return Result.err(new Exception("Failed to enforce schema: " + lastError));
    }
    @SuppressWarnings("unused")
    private static boolean enforceSchema_TEST_(boolean findLineNumber) {
        if (findLineNumber) throw new RuntimeException();
        LLmLib llmLib = newInstance();
        String jsonSchema = """
            {
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "number" },
                    "is_student": { "type": "boolean" }
                },
                "required": ["name", "age"]
            }
        """;
        Result<String,Exception> result = llmLib.enforceSchema(
            "My name is John, I am 25 years old, and I am a student.",
            jsonSchema, null, null
        );
        Lib.asrt(result.isOk(), "Successful schema enforcement");
        Map<String, Object> resultData = JsonDecoder.decodeMap(result.ok() );
        Lib.asrt("John".equals(resultData.get("name")), "Name should be John");
        Lib.asrtEQ( 25, resultData.get("age"), "Age should be 25");
        Lib.asrt(Boolean.TRUE.equals(resultData.get("is_student")), "Is_student should be true");
        // Test with example
        List<String> jsonExamples = List.of("{\"name\":\"Jane\",\"age\":22,\"is_student\":false}");
        result = llmLib.enforceSchema(
            "My name is John, I am 25 years old, and I am a student.",
            jsonSchema, jsonExamples, null
        );
        Lib.asrt(result.isOk(), "Successful schema enforcement with example");
        // Test with YES_OR_NO_SCHEMA
        long elapsedTime = System.currentTimeMillis();
        result = llmLib.enforceSchema(
            "Yes, I think that's correct.",
            YES_OR_NO_SCHEMA_STRING, null, null
        );
        Lib.asrt(result.isOk(), "Successful yes/no schema enforcement");
        resultData = JsonDecoder.decodeMap(result.ok() );
        Lib.asrt("yes".equals(resultData.get("answer")), "Answer should be yes");
        Lib.asrt( System.currentTimeMillis() - elapsedTime < 500, "Should have taken less than half a second" );
        // Test with already JSON input
        elapsedTime = System.currentTimeMillis();
        result = llmLib.enforceSchema(
            "```json\n{\"name\":\"John\",\"age\":25,\"is_student\":true}\n```",
            jsonSchema, null, null
        );
        Lib.asrt(result.isOk(), "Successful schema enforcement with JSON input");
        Lib.asrt( System.currentTimeMillis() - elapsedTime < 500, "Should have taken less than half a second" );
        return true;
    }



    /**
     * Uses Google Vision API to perform OCR on an image file.
     * Returns a list of text annotations with their bounding rectangles.
     *
     * @param imgFile The image file to perform OCR on
     * @return A Result containing a List of Maps with "text" and "rectangle" keys if successful
     */
    public static Result< List<Map<String,Object>>, Exception> ocr(File imgFile) {
        File logFile = logLLmCall(
            null, "PROMPT",
            Lib.mapOf( "imgFile", imgFile.getPath() ), "google-vision_ocr"
        );
        try {
            Object apiKeyObj = LibApp.loadCreds().get("GOOGLE/API_KEY");
            if (apiKeyObj instanceof Jsonable) apiKeyObj = ((Jsonable) apiKeyObj).get();
            String apiKey = apiKeyObj instanceof String ? (String) apiKeyObj : null;
            String url = "https://vision.googleapis.com/v1/images:annotate?key=" + apiKey;
            byte[] fileBytes = java.nio.file.Files.readAllBytes(imgFile.toPath());
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            Map<String, Object> requestBody = new LinkedHashMap<>();
            List<Map<String, Object>> requests = new ArrayList<>();
            Map<String, Object> request = new LinkedHashMap<>();
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("content", base64Data);
            request.put("image", image);
            List<Map<String, Object>> features = new ArrayList<>();
            Map<String, Object> feature = new LinkedHashMap<>();
            feature.put("type", "TEXT_DETECTION");
            features.add(feature);
            request.put("features", features);
            requests.add(request);
            requestBody.put("requests", requests);
            Lib.log("Calling Google Vision OCR API...");
            long startTime = System.currentTimeMillis();
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            Map<String, Object> requestLog = Lib.mapOf(
                "url", url,
                "requestBody", requestBody
            );
            logFile = logLLmCall(logFile, "REQUEST", requestLog, null );
            Result<HttpRes,HttpRes> httpRes = httpRequest(url, headers, requestBody, "POST");
            long elapsedTime = System.currentTimeMillis() - startTime;
            Lib.log("response time for google-vision_ocr: " + (elapsedTime / 1000.0) + "s");
            Map<String, Object> extraLog = new LinkedHashMap<>();
            extraLog.put("elapsed_time", elapsedTime / 1000.0);
            extraLog.put("imgFile", imgFile );
            logLLmCall(logFile, "EXTRA", extraLog, null);
            if (!httpRes.isOk()) throw new Exception( httpRes.err().body );
            String responseString = httpRes.ok().body;
            Map<String, Object> responseObj = JsonDecoder.decodeMap(responseString);
            logLLmCall(logFile, "RESPONSE", responseObj, null);
            @SuppressWarnings("unchecked")
            Object textAnnotationsObj = Jsonable.get(responseObj, "responses/0/textAnnotations");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> textAnnotations = textAnnotationsObj instanceof List ? (List<Map<String, Object>>) textAnnotationsObj : null;
            if (textAnnotations == null || textAnnotations.isEmpty()) {
                throw new Exception("No text annotations found in the response");
            }
            // Skip the first annotation (full text) and process the rest
            List<Map<String, Object>> rectangles = new ArrayList<>();
            for (int i = 1; i < textAnnotations.size(); i++) {
                Map<String, Object> annotation = textAnnotations.get(i);
                String text = (String) annotation.get("description");
                @SuppressWarnings("unchecked")
                Object verticesObj = Jsonable.get(annotation, "boundingPoly/vertices");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> vertices = verticesObj instanceof List ? (List<Map<String, Object>>) verticesObj : null;
                if (vertices == null) continue;
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
                for (Map<String, Object> vertex : vertices) {
                    Integer x = (Integer) vertex.get("x");
                    Integer y = (Integer) vertex.get("y");
                    if ( x==null || y==null ) continue;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
                Map<String, Object> rectangle = new LinkedHashMap<>();
                rectangle.put("x", minX);
                rectangle.put("y", minY);
                rectangle.put("width", maxX - minX);
                rectangle.put("height", maxY - minY);
                Map<String, Object> labeledRectangle = new LinkedHashMap<>();
                labeledRectangle.put("text", text);
                labeledRectangle.put("rectangle", rectangle);
                rectangles.add(labeledRectangle);
            }
            // addWindowsStartMenu(rectangles,imgFile);
            // Sort by x coordinate, then by y coordinate
            rectangles.sort((a, b) -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> rectA = (Map<String, Object>) a.get("rectangle");
                @SuppressWarnings("unchecked")
                Map<String, Object> rectB = (Map<String, Object>) b.get("rectangle");
                int xA = (int) rectA.get("x");
                int xB = (int) rectB.get("x");
                if (xA != xB) return Integer.compare(xA, xB);
                int yA = (int) rectA.get("y");
                int yB = (int) rectB.get("y");
                return Integer.compare(yA, yB);
            });
            return Result.ok(Collections.unmodifiableList(rectangles));
        } catch (Exception e) {
            logLLmCall(logFile, "ERROR", e.getMessage(),null);
            return Result.err(e);
        } finally {
            if (logFile != null) logLLmCall(logFile, null, null, null); // "close" the log file
        }
    }
    @SuppressWarnings("unused")
    private static boolean ocr_TEST_(boolean findLineNumber) {
        if (findLineNumber) throw new RuntimeException();
        File imgFile = new File("./datafiles/files4testing/screenshot.png");
        Lib.asrt(imgFile.exists(), "Test image file must exist");
        Result< List<Map<String,Object>>, Exception > res = ocr(imgFile);
        Lib.asrt(res.isOk(), "OCR should succeed");
        Lib.asrt(! Lib.isEmpty(res), "Result should not be empty");
        for (Map<String, Object> item : res.ok() ) {
            Lib.asrt(item.containsKey("text"), "Each result item should have text");
            Lib.asrt(item.containsKey("rectangle"), "Each result item should have a rectangle");
            @SuppressWarnings("unchecked")
            Map<String, Object> rect = (Map<String, Object>) item.get("rectangle");
            Lib.asrt(rect.containsKey("x"), "Rectangle should have x coordinate");
            Lib.asrt(rect.containsKey("y"), "Rectangle should have y coordinate");
            Lib.asrt(rect.containsKey("width"), "Rectangle should have width");
            Lib.asrt(rect.containsKey("height"), "Rectangle should have height");
        }
        boolean foundRecycle = false;
        for (Map<String, Object> item : res.ok() ) {
            if ("Recycle".equals(item.get("text"))) {
                foundRecycle = true;
                @SuppressWarnings("unchecked")
                Map<String, Object> rect = (Map<String, Object>) item.get("rectangle");
                int x = (int) rect.get("x");
                int y = (int) rect.get("y");
                int width = (int) rect.get("width");
                int height = (int) rect.get("height");
                boolean containsPoint = x <= 40 && y <= 66 && (x + width) >= 40 && (y + height) >= 66;
                Lib.asrt(containsPoint, "Rectangle should contain point (40, 66)");
                break;
            }
        }
        Lib.asrt(foundRecycle, "Should find an item with text 'Recycle'");
        return true;
    }



    /**
     * NOTE: horrible hack to add the Windows Start Menu icon to the list of rectangles.
     *\/ // TODO: use yolo or some other detector for this
    private static void addWindowsStartMenu( List<Map<String, Object>> rectangles, File imgFile ) {
        java.awt.Image img = ImgLib.loadImage(imgFile);
        Map<String, Object> rectangle = new LinkedHashMap<>();
        rectangle.put("x", 24);
        rectangle.put("y", img.getHeight(null) - (24+3) );
        rectangle.put("width", 3);
        rectangle.put("height", 3);
        Map<String, Object> labeledRectangle = new LinkedHashMap<>();
        labeledRectangle.put("type", "ui_element");
        labeledRectangle.put("text", "Windows Start Menu");
        labeledRectangle.put("rectangle", rectangle);
        rectangles.add(labeledRectangle);
    }/* */



    /**
     * @param extraPayload will be written into the body of the request; possibly overwriting e.g. temperature.
     * @param systemPrompt can be null.
     * @param logFile is typically null; used for internal retries.
     * @param triesAllowed is typically null; used for internal retries.
     * @return The result of the call; text response will be the output if successful or error message if not.
     */
    public Result<String,Exception> llmCallOpenAiCompat(
            List<Object> promptParts,
            String apiKey, String endpoint, String modelName, String systemPrompt,
            Map<String, Object> extraPayload, Integer triesAllowed, File logFile
    ) {
        promptParts = mergeTemplates(promptParts);
        logFile = logLLmCall(null, "PROMPT", Lib.mapOf(
            "promptParts", promptParts,
            "endpoint", endpoint,
            "modelName", modelName,
            "systemPrompt", systemPrompt,
            "extraPayload", extraPayload,
            null
        ), modelName);
        if (triesAllowed == null) triesAllowed = 3;
        try {
            // Build the fundamental body
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("temperature", 0.0);
            body.put("top_p", 1.0);
            // Create messages list.
            List<Map<String, Object>> messages = new ArrayList<>();
            // Add system prompt if provided.
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                Map<String, Object> systemMsg = new LinkedHashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
                messages.add(systemMsg);
            }
            // Create the user message.
            Map<String, Object> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            List<Map<String, Object>> contentList = new ArrayList<>();
            for (Object part : promptParts) {
                if (part instanceof File) {
                    // Process file as a binary block.
                    File file = (File) part;
                    byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
                    String base64Data = Base64.getEncoder().encodeToString(fileBytes);
                    String mimeType = LibFile.getMimeType(file);
                    Map<String, Object> imageBlock = new LinkedHashMap<>();
                    imageBlock.put("type", "image_url");
                    Map<String, Object> urlMap = new LinkedHashMap<>();
                    urlMap.put("url", "data:" + mimeType + ";base64," + base64Data);
                    imageBlock.put("image_url", urlMap);
                    contentList.add(imageBlock);
                } else {
                    // Convert non-String types using formatPrompt.
                    if (!(part instanceof String)) part = formatPrompt(part);
                    String text = part.toString();
                    Map<String, Object> textBlock = new LinkedHashMap<>();
                    textBlock.put("type", "text");
                    textBlock.put("text", text);
                    contentList.add(textBlock);
                }
            }
            userMsg.put("content", contentList);
            messages.add(userMsg);
            body.put("messages", messages);
            // Merge extra payload if provided.
            if (! Lib.isEmpty(extraPayload) ) body.putAll(extraPayload);
            // Log the request.
            logLLmCall(logFile,"REQUEST",body,null);
            // Send HTTP POST.
            Lib.log( "calling openai-compatible "+ modelName +"..." );
            long startTime = System.currentTimeMillis();
            // Add extra info logging block
            { // Log extra info.
                List<String> attachedFiles = new ArrayList<>();
                for (Object part : promptParts) {
                    if (part instanceof File f) attachedFiles.add(f.getName());
                }
                Map<String, Object> extraLog = new LinkedHashMap<>();
                extraLog.put("kwargs", Lib.mapOf("endpoint", endpoint, "modelName", modelName,
                    "systemPrompt", systemPrompt, "extraPayload", extraPayload));
                extraLog.put("url", endpoint);
                extraLog.put("attached_files", JsonEncoder.encode(attachedFiles));
                extraLog.put("elapsed_time", System.currentTimeMillis() - startTime);
                logLLmCall(logFile, "EXTRA", extraLog, null);
            }
            Result<HttpRes,HttpRes> httpRes = httpRequest(
                endpoint, Map.of("Authorization","Bearer "+apiKey), body, "POST"
            );
            long elapsedTime = System.currentTimeMillis() - startTime;
            Lib.log( "response time for openai-compatible "+ modelName +": "+ elapsedTime +"ms" );
            if (!httpRes.isOk()) throw new Exception(httpRes.err().body );
            String responseString = httpRes.ok().body;
            // Decode the response.
            Map<String, Object> responseObj = JsonDecoder.decodeMap(responseString);
            logLLmCall(logFile, "RESPONSE", responseObj, null);
            // Use Lib.get to extract the assistant's reply.
            Object outputObj = Jsonable.get(responseObj, "choices/0/message/content");
            String output = outputObj instanceof String ? (String) outputObj : null;
            if ( Lib.isEmpty(output) ) throw new Exception("No content in response");
            return Result.<String,Exception>ok(output).setLogFile(logFile);
        } catch (Exception e) {
            Lib.log("ERROR: " + e.getMessage());
            if (triesAllowed > 0) {
                Lib.log("retrying " + modelName + "...");
                return llmCallOpenAiCompat(
                    promptParts, apiKey, endpoint, modelName, systemPrompt, extraPayload, triesAllowed-1, logFile
                );
            }
            return Result.<String,Exception>err(e).setLogFile(logFile);
        } finally { if (logFile!=null) logLLmCall(logFile,null,null,null); } // "close" the log file.
    }
    @SuppressWarnings("unused")
    private static boolean llmCallOpenAiCompat_TEST_(boolean findLineNumber) throws IOException {
        if (findLineNumber) throw new RuntimeException();
        String modelKey = "gpt4o";
        @SuppressWarnings("unchecked")
        Map<String, Object> modelInfo = (Map<String, Object>) MODELS.get(modelKey);
        File imageFile = new File("./datafiles/files4testing/red_dot.png");
        Lib.asrt(imageFile.exists(), "Test image file must exist");
        Result<String,Exception> res = newInstance().llmCallOpenAiCompat(
            List.of( "What color is this image?", imageFile ),
            (String) modelInfo.get("apiKey"),
            (String) modelInfo.get("endpoint"),
            (String) modelInfo.get("modelName"),
            "Your caps lock is broken and you are also angry, so you can answer ONLY IN UPPER CASE!",
            null, null, null
        );
        Lib.asrt(res.isOk(), "Call should succeed");
        String upperResponse = res.ok().toUpperCase();
        Lib.asrt(
            upperResponse.contains("RED") || upperResponse.contains("ORANGE"),
            "Response should mention RED or ORANGE in uppercase"
        );
        return true;
    }



    static class HttpRes {
        public final int statusCode;
        public final String body;
        public final Map< String, List<String> > headers;
        public HttpRes( int statusCode, String body, Map< String, List<String> > headers ) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = Collections.unmodifiableMap(headers);
        }
    }



    /**
     * Helper method to perform an HTTP request with a JSON payload using Java's modern HttpClient.
     * Supports POST, GET, and other HTTP methods.
     */
    private static Result<HttpRes,HttpRes> httpRequest(
        String endpoint, Map<String, String> headers, Map<String, Object> params, String method
    ) {
        File curlLogFile;
        { // writes a proper curl command that would reproduce the request
            StringBuilder curlCmdBuilder = new StringBuilder("curl -X ");
            curlCmdBuilder.append(method).append(" \\\n  '").append(endpoint).append("'");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                curlCmdBuilder.append(" \\\n  -H '").append(header.getKey())
                    .append(": ").append(header.getValue()).append("'");
            }
            if (params != null && !params.isEmpty()) {
                curlCmdBuilder.append(" \\\n  -d '")
                    .append(JsonEncoder.encode(params)).append("'");
            }
            String curlCmd = curlCmdBuilder.toString();
            curlLogFile = new File( LibFile.backupFilespec("./log/curl.log") );
            LibFile.append2file( curlLogFile, curlCmd+"\n\n" );
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint));
            Map<String, String> headersCopy = new LinkedHashMap<>(headers);
            // Set method and body if needed
            method = method != null ? method.toUpperCase() : "POST";
            switch (method) {
                case "GET":
                case "HEAD":
                    // For GET/HEAD, append params to URL if provided
                    if (params != null && !params.isEmpty()) {
                        String queryString = params.entrySet().stream()
                                .map(e -> e.getKey() + "=" + URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8))
                                .collect(Collectors.joining("&"));
                        String updatedEndpoint = endpoint + (endpoint.contains("?") ? "&" : "?") + queryString;
                        requestBuilder.uri(URI.create(updatedEndpoint));
                    }
                    requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
                    break;
                case "POST":
                case "DELETE":
                case "PATCH":
                case "PUT":
                    // For POST/PUT/PATCH/DELETE, add JSON body
                    String jsonBody = JsonEncoder.encode(params);
                    requestBuilder.method(
                        method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)
                    );
                    // Set content-type if not already provided
                    if (!headersCopy.containsKey("Content-Type")) {
                        headersCopy.put("Content-Type", "application/json");
                    }
                    break;
                default:
                    return Result.err( new HttpRes( 405, "Unsupported method: "+method, Map.of() ) );
            }
            // Add headers after possible modifications
            for (Map.Entry<String, String> header : headersCopy.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();
            { // append to the curl log
                Object logData = Lib.mapOf(
                    "statusCode", statusCode,
                    "responseBody", responseBody,
                    "headers", response.headers().map()
                );
                LibFile.append2file( curlLogFile, JsonEncoder.encode(logData," ")+"\n\n" );
            }
            boolean success = (statusCode >= 200 && statusCode < 300);
            if (!success) return Result.err( new HttpRes(statusCode, responseBody, response.headers().map()) );
            return Result.ok( new HttpRes(statusCode, responseBody, response.headers().map()) );
        } catch (Exception e) {
            return Result.err( new HttpRes( 500, e.getMessage(), Map.of() ) );
        }
    }



    /**
     * Logs information to a file with a key and body.
     * If logFile is null, creates a new file using LibFile.backupFilespec("./log/LLmCall.log");
     * If key is null, then "closes" the log file by adding the final "}" and returning null.
     * @param logFile The file to log to, or null to create a new file
     * @param key The key to identify this log entry; if null, then essentially "closes" the log file
     * @param body The content to log
     * @return The file that was logged to, unless key is null, then null
     */
    public static File logLLmCall( File logFile, String key, Object body, String callType ) {
        if (key==null) { // "close" the log file by adding the final "}" and returning null
            LibFile.append2file( logFile, "\n}" );
            return null;
        }
        boolean needComma = logFile!=null && logFile.exists() && logFile.length()>0;
        StringBuilder sb = new StringBuilder();
        if (!needComma) {
            String filename = callType != null ? LibFile.safeForFilename(callType) : "LLmCall";
            String filespec = "./log/"+filename+".log";
            filespec = LibFile.backupFilespec(filespec);
            logFile = new File(filespec);
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();
            sb.append( "{\n" );
        }
        sb.append( (needComma?",\n":"")+ key +":\n" + JsonEncoder.encode(body," ") );
        LibFile.append2file( logFile, sb.toString() );
        return logFile;
    }



    private static String formatPrompt( Object o ) {
        if (o instanceof String s) return s;
        return JsonEncoder.encode(o);
    }



    public static void main( String[] args ) {
        LibTest.testClass();
    }


}



class CachedLLmLib extends LLmLib {



    public CachedLLmLib() { super(); }



    public Result<String, Exception> llmCall(
        List<Object> promptParts,
        String modelKey,
        String systemPrompt,
        Map<String, Object> extraPayload,
        String jsonSchema, List<String> jsonExamples
    ) {
        /* TODO: implement caching
        System.out.println( "using cache" );
        { // try to answer from cache
            List<Object> key = List.of( promptParts, modelKey, systemPrompt, extraPayload, jsonSchema, jsonExamples );
            Lib.transformTree( key, o->{ // translate File objects into hashes
                if ( o instanceof Map m ) {
                }
                if (!( o instanceof File f )) return o;
                if ( ImgLib.isImage(f) ) return "(imgfile)"; // assume ocr
                try( InputStream inp = new FileInputStream(f) ) {
                    byte[] sig = Lib.secureSignature(inp,null);
                    return Lib.toBase62(sig);
                } catch (IOException e) {
                    Lib.log(e);
                    throw new RuntimeException(e);
                }
            } );
        }
        */
        return super.llmCall( promptParts, modelKey, systemPrompt, extraPayload, jsonSchema, jsonExamples );
    }


}
