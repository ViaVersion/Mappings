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
package com.viaversion.mappingsgenerator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerJarUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerJarUtil.class.getSimpleName());

    public static void waitForServerMain() throws InterruptedException {
        final Thread serverMain = threadByName("ServerMain");
        if (serverMain == null) {
            return;
        }

        int i = 0;
        while (serverMain.isAlive()) {
            Thread.sleep(50);
            if (i++ * 50 > 30_000) {
                LOGGER.error("Something definitely went wrong (waited over 30 seconds for the server main to start)");
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

    public static Class<?> loadMain(final ClassLoader classLoader) throws ClassNotFoundException {
        System.setProperty("bundlerMainClass", "net.minecraft.data.Main");
        try {
            return classLoader.loadClass("net.minecraft.bundler.Main");
        } catch (final ClassNotFoundException ignored) {
            return classLoader.loadClass("net.minecraft.data.Main");
        }
    }
}
