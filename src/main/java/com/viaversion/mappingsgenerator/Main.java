/*
 * This file is part of ViaVersion Mappings - https://github.com/ViaVersion/Mappings
 * Copyright (C) 2023 Nassim Jahnke
 * Copyright (C) 2023 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.viaversion.mappingsgenerator;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public final class Main {

    private static final String[] ARGS = {"--reports"};

    public static void main(final String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Required args: path to server jar, version");
        }

        final String serverPath = args[0];
        final String version = args[1];
        MappingsGenerator.cleanup();

        final File serverFile = new File(serverPath);
        if (!serverFile.exists()) {
            System.err.println("Server file does not exist at " + serverFile);
            System.exit(1);
        }

        System.out.println("Loading net.minecraft.data.Main class...");
        final ClassLoader loader = URLClassLoader.newInstance(
                new URL[]{serverFile.toURI().toURL()},
                Main.class.getClassLoader()
        );

        final Object o = loadMain(loader).getConstructor().newInstance();
        final Object[] mainArgs = {ARGS};
        o.getClass().getDeclaredMethod("main", String[].class).invoke(null, mainArgs);

        waitForServerMain();

        MappingsGenerator.collectMappings(version);
    }

    public static void waitForServerMain() throws InterruptedException {
        final Thread serverMain = threadByName("ServerMain");
        if (serverMain == null) {
            return;
        }

        int i = 0;
        while (serverMain.isAlive()) {
            Thread.sleep(50);
            if (i++ * 50 > 30_000) {
                System.err.println("Something definitely went wrong");
                System.exit(1);
            }
        }
    }

    private static Thread threadByName(final String name) {
        for (final Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().equals(name)) {
                return thread;
            }
        }
        return null;
    }

    private static Class<?> loadMain(final ClassLoader classLoader) throws ClassNotFoundException {
        System.setProperty("bundlerMainClass", "net.minecraft.data.Main");
        try {
            return classLoader.loadClass("net.minecraft.bundler.Main");
        } catch (final ClassNotFoundException ignored) {
            return classLoader.loadClass("net.minecraft.data.Main");
        }
    }
}
