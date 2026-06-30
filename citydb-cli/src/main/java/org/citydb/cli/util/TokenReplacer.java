/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.util;

import org.citydb.core.file.InputFile;
import org.citydb.core.file.OutputFile;
import org.citydb.core.file.output.ZipOutputFile;
import org.citydb.util.tiling.Tile;

import java.io.File;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenReplacer {
    private static final Pattern FILE_TOKEN_PATTERN = Pattern.compile(
            "@(file_path|file_name|file_base_name|content_path|content_name|content_base_name)@",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TILE_TOKEN_PATTERN = Pattern.compile(
            "@(?:column|row|x_min|y_min|x_max|y_max)(?:,.+?)?@",
            Pattern.CASE_INSENSITIVE);

    public static String replaceFileTokens(String input, InputFile inputFile) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = FILE_TOKEN_PATTERN.matcher(input);

        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = switch (token.toLowerCase(Locale.ROOT)) {
                case "file_path" -> inputFile.getFile().toString();
                case "file_name" -> inputFile.getFile().getFileName().toString();
                case "file_base_name" -> extractBaseName(inputFile.getFile().getFileName().toString());
                case "content_path" -> inputFile.getContentFile();
                case "content_name" -> extractFileName(inputFile.getContentFile());
                case "content_base_name" -> extractBaseName(extractFileName(inputFile.getContentFile()));
                default -> token;
            };

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    public static String replaceFileTokens(String input, OutputFile outputFile) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = FILE_TOKEN_PATTERN.matcher(input);

        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = switch (token.toLowerCase(Locale.ROOT)) {
                case "file_path" -> outputFile.getFile().toString();
                case "file_name" -> outputFile.getFile().getFileName().toString();
                case "file_base_name" -> extractBaseName(outputFile.getFile().getFileName().toString());
                case "content_path" -> getContentPath(outputFile);
                case "content_name" -> extractFileName(getContentPath(outputFile));
                case "content_base_name" -> extractBaseName(extractFileName(getContentPath(outputFile)));
                default -> token;
            };

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    public static String replaceTileTokens(String input, Tile tile) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = TILE_TOKEN_PATTERN.matcher(input);

        while (matcher.find()) {
            String replacement = resolveTileToken(matcher.group(0), tile);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private static String resolveTileToken(String token, Tile tile) {
        String[] parts = token.substring(1, token.length() - 1).split(",", 2);
        String format = parts.length == 2 ? parts[1].trim() : "%s";
        Object value = switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "column" -> tile.getColumn();
            case "row" -> tile.getRow();
            case "x_min" -> tile.getExtent().getLowerCorner().getX();
            case "y_min" -> tile.getExtent().getLowerCorner().getY();
            case "x_max" -> tile.getExtent().getUpperCorner().getX();
            case "y_max" -> tile.getExtent().getUpperCorner().getY();
            default -> token;
        };

        try {
            return String.format(Locale.ENGLISH, format, value);
        } catch (IllegalFormatException e) {
            return String.valueOf(value);
        }
    }

    private static String getContentPath(OutputFile outputFile) {
        return outputFile instanceof ZipOutputFile zipOutputFile
                ? zipOutputFile.getFile().resolve(zipOutputFile.getContentFile()).toString()
                : outputFile.getFile().toString();
    }

    private static String extractFileName(String path) {
        String separator = File.separator;
        int index = path.lastIndexOf(separator);
        return index >= 0 ? path.substring(index + separator.length()) : path;
    }

    private static String extractBaseName(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }
}
