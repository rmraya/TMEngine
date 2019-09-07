/*******************************************************************************
 * Copyright (c) 2003 - 2019 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/
package com.maxprograms.tmserver;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.ParserConfigurationException;

import com.maxprograms.server.IServer;
import com.maxprograms.tmengine.Constants;
import com.maxprograms.tmengine.ITmEngine;
import com.maxprograms.tmengine.MapDbEngine;
import com.maxprograms.tmengine.SQLEngine;
import com.maxprograms.tmutils.TMUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

public class TmHandler implements HttpHandler {

    private static final Logger LOGGER = System.getLogger(TmHandler.class.getName());
    private ConcurrentHashMap<String, JSONObject> memories = new ConcurrentHashMap<>();;
    private ConcurrentHashMap<String, ITmEngine> openEngines = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, String[]> openTasks = new ConcurrentHashMap<>();
    private IServer server;
    private String prefix;
    private static boolean firstRun = true;

    public TmHandler(IServer server, String prefix) throws IOException {
        this.server = server;
        this.prefix = prefix;
        openEngines = new ConcurrentHashMap<>();
        openTasks = new ConcurrentHashMap<>();
        loadMemoriesList();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String request = "";
        try (InputStream is = exchange.getRequestBody()) {
            request = readRequestBody(is);
        }

        String response = "";
        
        response = processRequest(uri.toString(), request).toString(2);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length + 1l);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(response.getBytes())))) {
            try (OutputStream os = exchange.getResponseBody()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    os.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        if ((prefix + "/stop").equals(uri.toString())) {
            server.stop();
        }
    }

    private static String readRequestBody(InputStream is) throws IOException {
        StringBuilder request = new StringBuilder();
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = rd.readLine()) != null) {
                request.append(line);
            }
        }
        return request.toString();
    }

    private JSONObject processRequest(String url, String request) {
        JSONObject response = new JSONObject();
        try {
            if ((prefix + "/stop").equals(url)) {
                response = closeOpenEngines();
            } else if ((prefix + "/create").equals(url)) {
                response = createMemory(request);
            } else if ((prefix + "/open").equals(url)) {
                response = openMemory(request);
            } else if ((prefix + "/close").equals(url)) {
                response = closeMemory(request);
            } else if ((prefix + "/rename").equals(url)) {
                response = renameMemory(request);
            } else if ((prefix + "/list").equals(url)) {
                response = listMemories(request);
            } else if ((prefix + "/delete").equals(url)) {
                response = deleteMemory(request);
            } else if ((prefix + "/export").equals(url)) {
                response = exportMemory(request);
            } else if ((prefix + "/import").equals(url)) {
                response = importTMX(request);
            } else if ((prefix + "/flag").equals(url)) {
                response = flag(request);
            } else if ((prefix + "/search").equals(url)) {
                response = searchTranslations(request);
            } else if ((prefix + "/concordance").equals(url)) {
                response = concordanceSearch(request);
            } else if ((prefix + "/status").equals(url)) {
                response = getProcessStatus(request);
            } else if ((prefix + "/languages").equals(url)) {
                response = getLanguages(request);
            } else {
                response.put("reason", "Unknown request");
            }
            if (!response.has("reason")) {
                response.put("status", "OK");
            } else {
                response.put("status", "failed");
            }
        } catch (Exception j) {
            LOGGER.log(Level.ERROR, j.getMessage(), j);
            response.put("status", "failed");
            response.put("reason", j.getMessage());
        }
        return response;
    }

    private JSONObject openMemory(String request) throws IOException {
        JSONObject result = new JSONObject();
        try {
            JSONObject json = new JSONObject(request);
            if (!json.has("id")) {
                result.put("reason", "Missing memory id");
                return result;
            }
            open(json.getString("id"));
        } catch (IOException | JSONException | SQLException e) {
            LOGGER.log(Level.ERROR, "Error opening memory", e);
            result.put("reason", e.getMessage());
        }
        return result;
    }

    private JSONObject closeMemory(String request) {
        JSONObject result = new JSONObject();
        try {
            if (openEngines.isEmpty()) {
                result.put("reason", "No open memories");
                return result;
            }
            JSONObject json = new JSONObject(request);
            if (!json.has("id")) {
                result.put("reason", "Missing memory id");
                return result;
            }
            close(json.getString("id"));
        } catch (IOException | JSONException | SQLException e) {
            LOGGER.log(Level.ERROR, "Error opening memory", e);
            result.put("reason", e.getMessage());
        }
        return result;
    }

    private JSONObject renameMemory(String request) {
        JSONObject result = new JSONObject();
        try {
            JSONObject json = new JSONObject(request);
            if (!json.has("id")) {
                result.put("reason", "Missing memory id");
                return result;
            }
            if (!json.has("name")) {
                result.put("reason", "Missing new memory name");
                return result;
            }
            String id = json.getString("id");
            JSONObject mem = memories.get(id);
            if ("MapDbEngine".equals(mem.getString("type"))) {
                mem.put("name", json.getString("name"));
                memories.put(id, mem);
                saveMemoriesList();
            } else {
                result.put("reason", "Wrong memory type");
            }
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Error reanaming memory", e);
            result.put("reason", e.getMessage());
        }
        return result;
    }

    private JSONObject listMemories(String request) {
        JSONObject result = new JSONObject();
        JSONArray array = new JSONArray();
        Enumeration<String> keys = memories.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            JSONObject mem = memories.get(key);
            mem.put("isOpen", openEngines.containsKey(key));
            array.put(mem);
        }
        result.put("memories", array);
        return result;
    }

    private JSONObject flag(String request) {
        JSONObject result = new JSONObject();
        final JSONObject json = new JSONObject(request);
        if (!json.has("id")) {
            result.put("reason", "Missing memory id");
            return result;
        }
        if (!json.has("tuid")) {
            result.put("reason", "Missing unit id");
            return result;
        }
        String id = json.getString("id");
        final String process = "" + System.currentTimeMillis();
        new Thread(() -> {
            try {
                boolean shouldClose = !openEngines.containsKey(id);
                if (shouldClose) {
                    open(id);
                }
                openEngines.get(id).flag(json.getString("tuid"));
                if (shouldClose) {
                    close(id);
                }
                openTasks.put(process, new String[] { Constants.COMPLETED });
            } catch (IOException | SQLException e) {
                LOGGER.log(Level.ERROR, "Error getting languages list", e);
                openTasks.put(process, new String[] { Constants.FAILED, e.getMessage() });
            }
        }).start();
        openTasks.put(process, new String[] { Constants.PENDING });
        result.put("process", process);
        return result;
    }

    private JSONObject searchTranslations(String request) {
        JSONObject result = new JSONObject();
        // TODO
        return result;
    }

    private JSONObject concordanceSearch(String request) {
        JSONObject result = new JSONObject();
        // TODO
        return result;
    }

    private JSONObject getLanguages(String request) {
        JSONObject result = new JSONObject();
        JSONObject json = new JSONObject(request);
        if (!json.has("id")) {
            result.put("reason", "Missing memory id");
            return result;
        }
        final String id = json.getString("id");
        final String process = "" + System.currentTimeMillis();
        new Thread(() -> {
            try {
                boolean shouldClose = !openEngines.containsKey(id);
                if (shouldClose) {
                    open(id);
                }
                JSONObject obj = new JSONObject();
                JSONArray array = new JSONArray();
                array.put(openEngines.get(id).getAllLanguages());
                obj.put("languages", array);
                if (shouldClose) {
                    close(id);
                }
                openTasks.put(process, new String[] { Constants.COMPLETED, obj.toString(2) });
            } catch (IOException | SQLException e) {
                LOGGER.log(Level.ERROR, "Error getting languages list", e);
                openTasks.put(process, new String[] { Constants.FAILED, e.getMessage() });
            }
        }).start();
        openTasks.put(process, new String[] { Constants.PENDING });
        result.put("process", process);
        return result;
    }

    private JSONObject getProcessStatus(String request) {
        JSONObject result = new JSONObject();
        JSONObject json = new JSONObject(request);
        if (!json.has("process")) {
            result.put("reason", "Missing 'process' parameter");
            return result;
        }
        String process = json.getString("process");
        if (openTasks.containsKey(process)) {
            String[] status = openTasks.get(process);
            result.put("result", status[0]);
            if (Constants.COMPLETED.equals(status[0]) && status.length > 1) {
                result.put("data", new JSONObject(status[1]));
            }
            if (Constants.FAILED.equals(status[0])) {
                result.put("reason", status[1]);
            }
        } else {
            result.put("result", Constants.FAILED);
            result.put("reason", "No such process: " + process);
        }
        return result;
    }

    private JSONObject exportMemory(String request) {
        JSONObject result = new JSONObject();
        final JSONObject json = new JSONObject(request);
        if (!json.has("id")) {
            result.put("reason", "Missing memory id");
            return result;
        }
        if (!json.has("file")) {
            result.put("reason", "Missing tmx file");
            return result;
        }
        if (!json.has("srcLang")) {
            result.put("reason", "Missing source language");
            return result;
        }
        final String id = json.getString("id");
        final String process = "" + System.currentTimeMillis();
        new Thread(() -> {
            try {
                final boolean shouldClose = !openEngines.containsKey(id);
                if (shouldClose) {
                    open(id);
                }
                Set<String> langs = null;
                if (json.has("langs")) {
                    langs = new TreeSet<>();
                    JSONArray array = json.getJSONArray("langs");
                    for (int i = 0; i < array.length(); i++) {
                        langs.add(array.getString(i));
                    }
                }
                Map<String, String> properties = null;
                if (json.has("properties")) {
                    properties = new HashMap<>();
                    JSONObject props = json.getJSONObject("properties");
                    Iterator<String> keys = props.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        properties.put(key, json.getString(key));
                    }
                }
                ITmEngine engine = openEngines.get(id);
                engine.exportMemory(json.getString("file"), langs, json.getString("srcLang"), properties);
                if (shouldClose) {
                    close(id);
                }
                openTasks.put(process, new String[] { Constants.COMPLETED, "" });
            } catch (IOException | SQLException | JSONException | SAXException | ParserConfigurationException e) {
                LOGGER.log(Level.WARNING, "Error exporting TMX file", e);
                openTasks.put(process, new String[] { Constants.FAILED, e.getMessage() });
            }
        }).start();
        openTasks.put(process, new String[] { Constants.PENDING });
        result.put("process", process);
        return result;
    }

    private JSONObject importTMX(String request) {
        JSONObject result = new JSONObject();
        final JSONObject json = new JSONObject(request);
        if (!json.has("id")) {
            result.put("reason", "Missing memory id");
            return result;
        }
        final String id = json.getString("id");
        final String process = "" + System.currentTimeMillis();
        new Thread(() -> {
            try {
                final boolean shouldClose = !openEngines.containsKey(id);
                if (shouldClose) {
                    open(id);
                }
                ITmEngine engine = openEngines.get(id);
                int count = engine.storeTMX(json.getString("file"), json.getString("project"),
                        json.getString("customer"), json.getString("subject"));
                if (shouldClose) {
                    close(id);
                }
                openTasks.put(process, new String[] { Constants.COMPLETED, "" + count });
            } catch (IOException | SQLException | JSONException | SAXException | ParserConfigurationException e) {
                LOGGER.log(Level.WARNING, "Error importing TMX file", e);
                openTasks.put(process, new String[] { Constants.FAILED, e.getMessage() });
            }
        }).start();
        openTasks.put(process, new String[] { Constants.PENDING });
        result.put("process", process);
        return result;
    }

    protected void open(String id) throws IOException, SQLException {
        if (!memories.containsKey(id)) {
            throw new IOException("Unknown memory");
        }
        JSONObject mem = memories.get(id);
        if ("MapDbEngine".equals(mem.getString("type"))) {
            openEngines.put(id, new MapDbEngine(id, getWorkFolder()));
        } else if ("SQLEngine".equals(mem.getString("type"))) {
            openEngines.put(id, new SQLEngine(mem.getString("name"), mem.getString("serverName"), mem.getInt("port"),
                    mem.getString("userName"), mem.getString("password")));
        } else {
            throw new IOException("Unknown memory type");
        }
    }

    protected void close(String id) throws IOException, SQLException {
        if (openEngines.containsKey(id)) {
            openEngines.get(id).close();
            openEngines.remove(id);
        }
    }

    private JSONObject deleteMemory(String request) {
        JSONObject result = new JSONObject();
        try {
            JSONObject json = new JSONObject(request);
            if (!json.has("id")) {
                result.put("reason", "Missing memory id");
                return result;
            }
            String id = json.getString("id");
            if (!memories.containsKey(id)) {
                result.put("reason", "Unknown memory");
                return result;
            }
            if (!openEngines.contains(id)) {
                open(id);
            }
            openEngines.get(id).deleteDatabase();
            openEngines.remove(id);
            memories.remove(id);
            saveMemoriesList();
        } catch (IOException | SQLException e) {
            LOGGER.log(Level.ERROR, "Error deleting memory", e);
            result.put("reason", e.getMessage());
        }
        return result;
    }

    private JSONObject closeOpenEngines() {
        if (openEngines.isEmpty()) {
            return new JSONObject();
        }
        Enumeration<String> keys = openEngines.keys();
        while (keys.hasMoreElements()) {
            try {
                close(keys.nextElement());
            } catch (IOException | SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing engine", e);
            }
        }
        return new JSONObject();
    }

    private synchronized void loadMemoriesList() throws IOException {
        memories = new ConcurrentHashMap<>();
        File home = new File(getWorkFolder());
        File list = new File(home, "memories.json");
        if (!list.exists()) {
            return;
        }
        StringBuffer buffer = new StringBuffer();
        try (FileReader input = new FileReader(list)) {
            try (BufferedReader reader = new BufferedReader(input)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                }
            }
        }
        JSONObject json = new JSONObject(buffer.toString());
        Iterator<String> it = json.keys();
        while (it.hasNext()) {
            String key = it.next();
            JSONObject obj = json.getJSONObject(key);
            memories.put(key, obj);
        }
        if (firstRun) {
            firstRun = false;
            new Thread(() -> {
                try {
                    File[] filesList = home.listFiles();
                    for (int i = 0; i < filesList.length; i++) {
                        if (filesList[i].isDirectory() && !memories.containsKey(filesList[i].getName())) {
                            TMUtils.deleteFolder(filesList[i].getAbsolutePath());
                        }
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error deleting folder", e);
                }
            }).start();
        }
    }

    private synchronized void saveMemoriesList() throws IOException {
        JSONObject json = new JSONObject();
        Set<String> keys = memories.keySet();
        Iterator<String> it = keys.iterator();
        while (it.hasNext()) {
            String key = it.next();
            JSONObject m = memories.get(key);
            json.put(key, m);
        }
        File home = new File(getWorkFolder());
        File list = new File(home, "memories.json");
        try (FileOutputStream out = new FileOutputStream(list)) {
            out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private JSONObject createMemory(String request) {
        JSONObject result = new JSONObject();
        JSONObject json = new JSONObject(request);
        try {
            if (!json.has("id")) {
                json.put("id", "" + System.currentTimeMillis());
            }
            if (memories.containsKey(json.getString("id"))) {
                result.put("reason", "Duplicated id");
                return result;
            }
            if (!json.has("creationDate")) {
                json.put("creationDate", System.currentTimeMillis());
            }
            if (!json.has("owner")) {
                json.put("owner", System.getProperty("user.name"));
            }
            if (!json.has("type")) {
                json.put("type", "MapDbEngine");
            }
            ITmEngine engine = null;
            if ("MapDbEngine".equals(json.getString("type"))) {
                engine = new MapDbEngine(json.getString("id"), getWorkFolder());
                engine.close();
            }
            if ("SQLEngine".equals(json.getString("type"))) {
                engine = new SQLEngine(json.getString("name"), json.getString("serverName"), json.getInt("port"),
                        json.getString("userName"), json.getString("password"));
                engine.close();
            }
            if (engine == null) {
                result.put("reason", "Unknown engine type");
                return result;
            }
            memories.put(json.getString("id"), json);
            saveMemoriesList();
            result.put("id", json.getString("id"));
        } catch (IOException | JSONException | SQLException e) {
            LOGGER.log(Level.ERROR, "Error creating memory", e);
            result.put("reason", e.getMessage());
        }
        return result;
    }

    private String getWorkFolder() throws IOException {
        File home = server.getWorkFolder();
        File workFolder = new File(home, "memories");
        if (!workFolder.exists()) {
            Files.createDirectories(workFolder.toPath());
        }
        return workFolder.getAbsolutePath();
    }
}