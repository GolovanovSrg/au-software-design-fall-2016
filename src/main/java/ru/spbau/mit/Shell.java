package ru.spbau.mit;

import ru.spbau.mit.command.Command;
import ru.spbau.mit.command.ExternalCmd;
import ru.spbau.mit.util.Pair;

import java.util.*;

/**
 * Created by Эдгар on 17.09.2016.
 */
public class Shell {

    private Map<String, Command> commands = new HashMap<>();
    private Map<String, String> env = new HashMap<>();

    /**
     *
     */
    private Shell() { }

    /**
     * @param input
     * @return
     */
    public String execute(String input) {
        List<String> pipeline = createPipeline(input);
        for (int i = 0; i < pipeline.size();) {
            Pair<String, String> assignment =
                    getAllVariablesFromCommand(pipeline.get(i));
            if (assignment == null) {
                pipeline.set(i, expandVars(pipeline.get(i)));
                ++i;
            } else {
                env.put(assignment.fst, assignment.snd);
                pipeline.remove(i);
            }
        }

        String output = "";

        if (!pipeline.isEmpty()) {
            String firstCmd = pipeline.get(0);
            Pair<Command, String> parsed = parseCommand(firstCmd);
            output = parsed.fst.execute(parsed.snd);
            for (int i = 1; i < pipeline.size(); ++i) {
                String cmd = pipeline.get(i);
                String tmp;
                parsed = parseCommand(cmd);
                if (parsed.snd == null) {
                    tmp = parsed.fst.execute(output);
                } else {
                    tmp = parsed.fst.execute(parsed.snd);
                }
                output = tmp;
            }
        }
        return output;
    }

    /**
     * @param rawInput
     * @return
     */
    private List<String> createPipeline(String rawInput) {
        List<String> pipeline = new ArrayList<>();
        boolean inStrongQuotes = false;
        boolean inWeakQuotes = false;
        int l = 0;
        for (int r = 0; r < rawInput.length(); ++r) {
            char chr = rawInput.charAt(r);
            if (!inStrongQuotes && !inWeakQuotes && chr == '|') {
                pipeline.add(rawInput.substring(l, r));
                l = r + 1;
            }
            if (chr == '\'') {
                inStrongQuotes ^= true;
            }
            if (chr == '\"') {
                inWeakQuotes ^= true;
            }
        }
        pipeline.add(rawInput.substring(l));
        pipeline.replaceAll(String::trim);
        return pipeline;
    }

    /**
     * @param rawCommand
     * @return
     */
    private String expandVars(String rawCommand) {
        List<Integer> placeholders = new ArrayList<>();
        boolean inStrongQuotes = false;
        for (int i = 0; i < rawCommand.length(); ++i) {
            if (!inStrongQuotes && rawCommand.charAt(i) == '$') {
                placeholders.add(i);
            }
            if (rawCommand.charAt(i) == '\'') {
                inStrongQuotes ^= true;
            }
        }

        StringBuilder expanded = new StringBuilder();
        Set<Character> delimiters = new HashSet<Character>()
        {{
            add(' ');
            add('"');
            add('=');
            add('\\');
        }};
        int left = 0;
        for (Integer right : placeholders) {
            expanded.append(rawCommand.substring(left, right));
            left = right + 1;
            while (left < rawCommand.length() && !delimiters.contains(rawCommand.charAt(left))) {
                ++left;
            }
            String name = rawCommand.substring(right + 1, left);
            if (env.get(name) != null) {
                expanded.append(env.get(name));
            } else {
                expanded.append("");
            }
        }
        return expanded.append(rawCommand.substring(left)).toString();
    }

    /**
     * @param rawCommand
     * @return
     */
    private Pair<String, String> getAllVariablesFromCommand(String rawCommand) {
        List<String> tmp = new ArrayList<>(Arrays.asList(rawCommand.split("\\s+")));
        tmp.removeAll(Collections.singleton(""));
        if (tmp.size() > 1) {
            return null;
        }
        String tmpString = tmp.get(0);
        tmp = Arrays.asList(tmpString.split("="));

        if (tmp.size() != 2) {
            return null;
        }

        return new Pair<>(tmp.get(0), tmp.get(1));
    }

    /**
     * @param rawCommand
     * @return
     */
    private Pair<Command, String> parseCommand(String rawCommand) {
        String[] split = rawCommand.split("\\s+");
        String cmdName = split[0];
        List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList(split).subList(1, split.length));
        if (commands.get(cmdName) != null) {
            String input = String.join(" ", args);
            return new Pair<>(commands.get(cmdName), input);
        } else {
            String cwd = commands.get("pwd").execute("");
            return new Pair<>(new ExternalCmd(env, cwd), rawCommand);
        }
    }

    public static class Builder {
        private Shell shell;

        /**
         *
         */
        public Builder() {
            shell = new Shell();
        }

        /**
         * @param name
         * @param cmd
         * @return
         */
        public Builder command(String name, Command cmd) {
            shell.commands.put(name, cmd);
            return this;
        }

        /**
         * @return
         */
        public Shell init() {
            return shell;
        }
    }
}