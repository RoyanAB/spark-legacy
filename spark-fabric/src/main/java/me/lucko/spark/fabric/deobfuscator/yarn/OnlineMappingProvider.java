/*
 * This file is part of the Carpet TIS Addition project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2023  Fallen_Breath and contributors
 *
 * Carpet TIS Addition is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Carpet TIS Addition is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Carpet TIS Addition.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.lucko.spark.fabric.deobfuscator.yarn;


import com.google.common.collect.Lists;
import com.google.common.net.UrlEscapers;
import com.google.gson.*;
import me.lucko.spark.fabric.FabricSparkMod;
import me.lucko.spark.fabric.deobfuscator.StackTraceDeobfuscator;
import me.lucko.spark.fabric.deobfuscator.utils.FileUtils;
import net.minecraft.util.Pair;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
public class OnlineMappingProvider
{
	private static final Logger LOGGER = FabricSparkMod.LOGGER;
	public static final String MINECRAFT_VERSION = "1.12.2";
	public static final String YARN_META_URL = "https://meta.ornithemc.net/v3/versions/feather/" + MINECRAFT_VERSION;
	public static final String YARN_MAPPING_URL_BASE = "https://maven.ornithemc.net/releases/net/ornithemc/feather/";
	public static final String MAPPINGS_JAR_LOCATION = "mappings/mappings.tiny";
	public static final String STORAGE_DIRECTORY = String.format("./config/%s/mapping/", "spark");
	public static final String YARN_VERSION_CACHE_FILE = STORAGE_DIRECTORY + "yarn_version.json";

	private static String getMappingFileName(String yarnVersion)
	{
		return String.format("feather-%s-v2", yarnVersion);
	}

	private static String getMappingFileNameFull(String yarnVersion)
	{
		return getMappingFileName(yarnVersion) + ".tiny";
	}

	private static String getYarnVersionOnline() throws IOException
	{
		URL url = URI.create(YARN_META_URL).toURL();
		URLConnection request = url.openConnection();
		List<Pair<Integer, String>> list = Lists.newArrayList();
		JsonElement json = JsonParser.parseReader(new InputStreamReader(request.getInputStream()));
		json.getAsJsonArray().forEach(e -> {
			JsonObject object = e.getAsJsonObject();
			list.add(new Pair<>(object.get("build").getAsInt(), object.get("version").getAsString()));
		});
		return list.stream().max(Comparator.comparingInt(Pair::getLeft)).orElseThrow(() -> new IOException("Empty list")).getRight();
	}

	synchronized private static String getYarnVersion(boolean useCache) throws IOException
	{
		List<YarnVersionCache> cacheList = Lists.newArrayList();

		// read
		File file = new File(YARN_VERSION_CACHE_FILE);
		if (FileUtils.isFile(file))
		{
			YarnVersionCache[] caches = null;
			try
			{
				caches = new Gson().fromJson(new InputStreamReader(Files.newInputStream(file.toPath())), YarnVersionCache[].class);
			}
			catch (Exception e)
			{
				LOGGER.warn("Failed to deserialize data from {}: {}", YARN_VERSION_CACHE_FILE, e);
			}
			if (caches != null)
			{
				cacheList.addAll(Arrays.asList(caches));
			}
		}

		// scan
		YarnVersionCache storedCache = null;
		for (YarnVersionCache cache : cacheList)
		{
			if (cache.minecraftVersion.equals(OnlineMappingProvider.MINECRAFT_VERSION))
			{
				storedCache = cache;
				break;
			}
		}
		if (useCache && storedCache != null)
		{
			LOGGER.debug("Found feather version from file cache");
			return storedCache.yarnVersion;
		}

		// download
		String yarnVersion = getYarnVersionOnline();
		cacheList.remove(storedCache);
		cacheList.add(new YarnVersionCache(OnlineMappingProvider.MINECRAFT_VERSION, yarnVersion));

		// store
		FileUtils.touchFileDirectory(file);
		OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()));
		writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(cacheList));
		writer.flush();
		writer.close();

		return yarnVersion;
	}

	synchronized private static FileInputStream getYarnMappingStream(String yarnVersion) throws IOException
	{
		File mappingFile = new File(STORAGE_DIRECTORY + getMappingFileNameFull(yarnVersion));
		if (!FileUtils.isFile(mappingFile))
		{
			String mappingJar = String.format("%s.jar", getMappingFileName(yarnVersion));
			String mappingJarUrl = String.format("%s%s/%s", YARN_MAPPING_URL_BASE, yarnVersion, mappingJar);
			String escapedUrl = UrlEscapers.urlFragmentEscaper().escape(mappingJarUrl);

			LOGGER.info("Downloading yarn mapping from {}", escapedUrl);
			File jarFile = new File(STORAGE_DIRECTORY + mappingJar);
			org.apache.commons.io.FileUtils.copyURLToFile(URI.create(escapedUrl).toURL(), jarFile);

			try (FileSystem jar = FileSystems.newFileSystem(jarFile.toPath(), (ClassLoader) null))
			{
				Files.copy(jar.getPath(MAPPINGS_JAR_LOCATION), mappingFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
 			}
			Files.delete(jarFile.toPath());
		}
		return new FileInputStream(mappingFile);
	}

	synchronized private static void loadMappings(InputStream mappingStream, String yarnVersion)
	{
		if (StackTraceDeobfuscator.loadMappings(mappingStream, "Feather " + yarnVersion))
		{
			LOGGER.info("Feather mapping file {} loaded", getMappingFileNameFull(yarnVersion));
		}
	}

	private static void getMappingThreaded()
	{
		try
		{
			// 1. Get yarn version
			String yarnVersion = getYarnVersion(true);
			LOGGER.debug("Got Feather version for Minecraft {}: {}", MINECRAFT_VERSION, yarnVersion);

			// 2. Get yarn mapping
			FileInputStream mappingStream = getYarnMappingStream(yarnVersion);

			// 3. Load yarn mapping
			loadMappings(mappingStream, yarnVersion);

		}
		catch (IOException e)
		{
			LOGGER.error("Failed to get feather mapping, the stack trace deobfuscator will not work: {}", e.toString());
		}
	}

	/**
	 * Entry point
	 */
	public static void getMapping()
	{
		startThread("Mapping", OnlineMappingProvider::getMappingThreaded);
	}

	public static void startThread(String threadName, Runnable runnable)
	{
		Thread thread = new Thread(runnable);
		if (threadName != null)
		{
			thread.setName(threadName);
		}
		thread.setDaemon(true);
		thread.start();
	}
}
