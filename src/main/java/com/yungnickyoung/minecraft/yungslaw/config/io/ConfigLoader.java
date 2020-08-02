package com.yungnickyoung.minecraft.yungslaw.config.io;


import com.yungnickyoung.minecraft.yungslaw.YungsLaw;
import com.yungnickyoung.minecraft.yungslaw.config.util.ConfigHolder;
import com.yungnickyoung.minecraft.yungslaw.config.util.ConfigOption;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utility class for populating a ConfigHolder from a config file.
 */
public class ConfigLoader {
    private static final String ALLOWED_CHARS = "._-";
    private static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * Loads a config from file for a given dimension.
     * @param dimensionID Unique dimension ID
     * @return ConfigHolder loaded from file for given dimension
     */
    public static ConfigHolder loadConfigFromFileForDimension(int dimensionID) {
        String fileName = "DIM" + dimensionID + "_config.cfg";
        File configFile = new File(YungsLaw.customConfigDir, fileName);

        if (!configFile.exists() || configFile.isDirectory()) {
            YungsLaw.LOGGER.info("YUNG's Law config file for dimension {} not found. Using global config...", dimensionID);
            return new ConfigHolder();
        }

        if (!configFile.canRead()) {
            YungsLaw.LOGGER.warn("YUNG's Law config file for dimension {} not readable. Using global config...", dimensionID);
            return new ConfigHolder();
        }

        YungsLaw.LOGGER.info("Reading YUNG's Law config from file for dimension {}...", dimensionID);
        return parseConfigFromFile(configFile);
    }

    /**
     * Reads a config file and returns a ConfigHolder with those options.
     * The file's format is expected to be compliant with Forge's config file structure.
     * @param file Config File
     * @return ConfigHolder populated with data in file. Any config options not specified in the config file will
     *         use the respective value in the global config
     */
    private static ConfigHolder parseConfigFromFile(File file) {
        ConfigHolder config = new ConfigHolder();
        BufferedReader buffer = null;
        Configuration.UnicodeInputStreamReader input = null;
        String fileName= file.getName();

        try {
            Map<String, ConfigCategory> categories = new TreeMap<>();

            input = new Configuration.UnicodeInputStreamReader(new FileInputStream(file), DEFAULT_ENCODING);
            buffer = new BufferedReader(input);

            String line;
            ConfigCategory currCategory = null;
            Property.Type type = null;
            ArrayList<String> tmpList = null;
            int lineNum = 0;
            String name = null;

            while (true) {
                lineNum++;
                line = buffer.readLine();

                if (line == null)
                    break;

                int nameStart = -1, nameEnd = -1;
                boolean skip = false;
                boolean quoted = false;
                boolean isTypeSpecified = false;
                boolean isFirstNonWhitespaceCharOnLine = true;

                for (int i = 0; i < line.length() && !skip; ++i) {
                    if (Character.isLetterOrDigit(line.charAt(i)) || ALLOWED_CHARS.indexOf(line.charAt(i)) != -1 || (quoted && line.charAt(i) != '"')) {
                        if (nameStart == -1)
                            nameStart = i;

                        nameEnd = i;
                        isFirstNonWhitespaceCharOnLine = false;
                    } else if (Character.isWhitespace(line.charAt(i))) {
                        // ignore space characters
                    } else {
                        switch (line.charAt(i)) {
                            case '#':
                                if (tmpList != null) // allow special characters as part of string lists
                                    break;

                                skip = true;
                                continue;
                            case '"':
                                if (tmpList != null) // allow special characters as part of string lists
                                    break;

                                if (quoted)
                                    quoted = false;

                                if (!quoted && nameStart == -1)
                                    quoted = true;
                                break;
                            case '{':
                                if (tmpList != null) // allow special characters as part of string lists
                                    break;

                                name = line.substring(nameStart, nameEnd + 1);
                                name = name.toLowerCase(Locale.ENGLISH);
                                String qualifiedName = ConfigCategory.getQualifiedName(name, currCategory);

                                ConfigCategory category = categories.get(qualifiedName);
                                if (category == null) {
                                    currCategory = new ConfigCategory(name, currCategory);
                                    categories.put(qualifiedName, currCategory);
                                } else {
                                    currCategory = category;
                                }

                                name = null;
                                break;
                            case '}':
                                if (tmpList != null) // allow special characters as part of string lists
                                    break;
                                if (currCategory == null)
                                    throw new RuntimeException(String.format("Invalid config file: attempted to close too many categories '%s:%d'", fileName, lineNum));

                                currCategory = currCategory.parent;
                                break;
                            case '=':
                                if (tmpList != null) // allow special characters as part of string lists
                                    break;

                                name = line.substring(nameStart, nameEnd + 1);

                                if (currCategory == null)
                                    throw new RuntimeException(String.format("'%s' has no scope (missing category?) in '%s:%d'", name, fileName, lineNum));

                                if (!isTypeSpecified) {
                                    YungsLaw.LOGGER.warn("Error in YUNG's Law config for {} (line {}): missing variable type specifier. Inferring String...", fileName, lineNum);
                                }

                                Property prop = new Property(name, line.substring(i + 1), type, true);
                                String fullName = currCategory.getQualifiedName().toLowerCase() + "." + name.toLowerCase();
                                ConfigOption target = config.properties.get(fullName);

                                if (target != null) {
                                    switch(type) {
                                        case INTEGER:
                                            if (!(target.type.equals(Integer.TYPE) || target.type.equals(Integer.class))) {
                                                YungsLaw.LOGGER.error("ERROR: WRONG TYPE for {} in config {}. Skipping...", fullName, fileName);
                                                i = line.length();
                                                continue;
                                            }
                                            target.set(prop.getInt());
                                            break;
                                        case DOUBLE:
                                            if (!(target.type.equals(Double.TYPE) || target.type.equals(Double.class) || target.type.equals(Float.TYPE) || target.type.equals(Float.class))) {
                                                YungsLaw.LOGGER.error("ERROR: WRONG TYPE for {} in config {}. Skipping...", fullName, fileName);
                                                i = line.length();
                                                continue;
                                            }
                                            if (target.type == Float.class)
                                                target.set((float)prop.getDouble());
                                            else
                                                target.set(prop.getDouble());
                                            break;
                                        case BOOLEAN:
                                            if (!(target.type.equals(Boolean.TYPE) || target.type.equals(Boolean.class))) {
                                                YungsLaw.LOGGER.error("ERROR: WRONG TYPE for {} in config {}. Skipping...", fullName, fileName);
                                                i = line.length();
                                                continue;
                                            }
                                            if (!(line.substring(i+ 1).toLowerCase().equals("true") || line.substring(i+1).toLowerCase().equals("false")))
                                                throw new RuntimeException(String.format("Invalid Boolean value for property '%s:%d'", fullName, lineNum));
                                            target.set(prop.getBoolean());
                                            break;
                                        default: // Strings or enums
                                            target.set(prop.getString());
                                    }
                                    currCategory.put(name, prop);
                                } else {
                                    YungsLaw.LOGGER.error("ERROR: INVALID PROPERTY {} in config {}. Skipping...", fullName, fileName);
                                    i = line.length();
                                    continue;
                                }

                                YungsLaw.LOGGER.debug("{}: overriding config option: {}", fileName, fullName);

                                i = line.length();
                                break;
                            case ':':
                                if (tmpList != null) // allow special characters as part of string lists
                                    break;

                                String typeSubstr = line.substring(nameStart, nameEnd + 1);
                                type = Property.Type.tryParse(typeSubstr.charAt(0));
                                nameStart = nameEnd = -1;
                                isTypeSpecified = true;

                                break;
                            case '<':
                                if ((tmpList != null && i + 1 == line.length()) || (tmpList == null && i + 1 != line.length())) {
                                    throw new RuntimeException(String.format("Malformed list property \"%s:%d\"", fileName, lineNum));
                                } else if (i + 1 == line.length()) {
                                    name = line.substring(nameStart, nameEnd + 1);
                                    if (currCategory == null)
                                        throw new RuntimeException(String.format("'%s' has no scope (missing category?) in '%s:%d'", name, fileName, lineNum));

                                    tmpList = new ArrayList<>();
                                    skip = true;
                                }

                                break;
                            case '>':
                                if (tmpList == null)
                                    throw new RuntimeException(String.format("Malformed list property \"%s:%d\"", fileName, lineNum));

                                if (isFirstNonWhitespaceCharOnLine) {
                                    prop = new Property(name, tmpList.toArray(new String[0]), type);
                                    currCategory.put(name, prop);

                                    fullName = currCategory.getQualifiedName().toLowerCase() + "." + name.toLowerCase();
                                    target = config.properties.get(fullName);
                                    if (target != null) {
                                        target.set(prop.getStringList());
                                        YungsLaw.LOGGER.debug("{}: overriding config option: {}", fileName, fullName);
                                    } else {
                                        YungsLaw.LOGGER.error("ERROR: INVALID LIST {} in config {}. Skipping...", fullName, fileName);
                                    }

                                    name = null;
                                    tmpList = null;
                                    type = null;
                                } // else allow special characters as part of string lists

                                break;
                            case '~':
                                if (tmpList != null) // allow special characters as part of string lists
                                    break;

                                break;
                            default:
                                if (tmpList != null) // allow special characters as part of string lists
                                    break;
                                throw new RuntimeException(String.format("Unknown character '%s' in '%s:%d'", line.charAt(i), fileName, lineNum));
                        }
                        isFirstNonWhitespaceCharOnLine = false;
                    }
                }
                if (quoted)
                    throw new RuntimeException(String.format("Unmatched quote in '%s:%d'", fileName, lineNum));
                else if (tmpList != null && !skip)
                    tmpList.add(line.trim());
            }
        } catch (Exception e) {
            YungsLaw.LOGGER.error("ERROR LOADING YUNG'S LAW CONFIG {}: {}.", fileName, e.toString());
            YungsLaw.LOGGER.info("USING GLOBAL CONFIG FILE INSTEAD...");
            return new ConfigHolder();
        } finally {
            IOUtils.closeQuietly(buffer);
            IOUtils.closeQuietly(input);
        }

        return config;
    }
}