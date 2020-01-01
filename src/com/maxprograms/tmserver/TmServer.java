/*******************************************************************************
 * Copyright (c) 2003-2020 Maxprograms.
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

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.nio.file.Files;

import com.maxprograms.converters.Utils;
import com.maxprograms.server.IServer;
import com.maxprograms.tmengine.Constants;
import com.sun.net.httpserver.HttpServer;

public class TmServer implements IServer {

    private static final Logger LOGGER = System.getLogger(TmServer.class.getName());

    private HttpServer server;
    private File workDir;

    public static void main(String[] args) {
        String[] arguments = Utils.fixPath(args);
        String port = "8000";
        for (int i = 0; i < arguments.length; i++) {
            String param = arguments[i];
            if (param.equals("-help")) {
                help();
                return;
            }
            if (param.equals("-version")) {
                LOGGER.log(Level.INFO, () -> "Version: " + Constants.VERSION + " Build: " + Constants.BUILD);
                return;
            }
            if (param.equals("-port") && (i + 1) < arguments.length) {
                port = arguments[i + 1];
            }
        }
        try {
            TmServer instance = new TmServer(Integer.valueOf(port));
            instance.run();
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Server error", e);
        }
    }

    private static void help() {
        String launcher = "    tmserver.sh ";
        if (System.getProperty("file.separator").equals("\\")) {
            launcher = "   tmserver.bat ";
        }
        String help = "Usage:\n\n" + launcher + "[-help] [-version] [-port portNumber]\n\n" + "  Where:\n\n"
                + "   -help:      (optional) Display this help information and exit\n"
                + "   -version:   (optional) Display version & build information and exit\n"
                + "   -port:      (optional) Port for running HTTP server. Default is 8000\n";
        System.out.println(help);
    }

    public TmServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/TMServer", new TmHandler(this, "/TMServer"));
        server.setExecutor(null); // creates a default executor
    }

    public void run() {
        server.start();
        LOGGER.log(Level.INFO, "TMEngine server started");
    }

    @Override
    public void stop() {
        server.removeContext("/TMServer");
        LOGGER.log(Level.INFO, "TMEngine server closed");
        System.exit(0);
    }

    @Override
    public File getWorkFolder() throws IOException {
        if (workDir == null) {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.startsWith("mac")) {
                workDir = new File(System.getProperty("user.home") + "/Library/Application Support/TMEngine/");
            } else if (os.startsWith("windows")) {
                workDir = new File(System.getenv("AppData") + "\\TMEngine\\");
            } else {
                workDir = new File(System.getProperty("user.home") + "/.tmengine/");
            }
            if (!workDir.exists()) {
                Files.createDirectories(workDir.toPath());
            }
        }
        return workDir;
    }
}