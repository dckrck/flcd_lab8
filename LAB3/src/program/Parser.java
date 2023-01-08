package program;

import java.util.*;

public class Parser {
    private final Grammar grammar;
    private HashMap<String, Set<String>> firstSet;
    private HashMap<String, Set<String>> followSet;
    private HashMap<Pair, Pair> parseTable;
    private List<List<String>> productionList;

    private int ERROR_CODE = -1;
    private int POP_CODE = -2;

    public Parser(Grammar grammar) {
        this.grammar = grammar;
        this.firstSet = new HashMap<>();
        this.followSet = new HashMap<>();
        this.parseTable = new HashMap<>();
        this.productionList = new ArrayList<>();

        first();
        follow();
        generateParseTable();
    }

    private Set<String> concatenate(List<String> nonTerminals, String terminal) {
        if (nonTerminals.size() == 0)
            return new HashSet<>();
        if (nonTerminals.size() == 1) {
            return firstSet.get(nonTerminals.iterator().next());
        }

        Set<String> concatenation = new HashSet<>();
        boolean allEpsilonInFirstSets = true;

        for (String nonTerminal : nonTerminals)
            if (!firstSet.get(nonTerminal).contains("epsilon"))
                allEpsilonInFirstSets = false;

        // 3. If FIRST (Yi) contains Є for all i = 1 to n, then add Є to FIRST(X).
        if (allEpsilonInFirstSets) {
            if (terminal == null) concatenation.add("epsilon");
            else concatenation.add(terminal);
        }

        int i = 0;
        // 2. If FIRST(Y1) contains Є then FIRST(X) = { FIRST(Y1) – Є } U { FIRST(Y2) }
        while (i < nonTerminals.size()) {
            boolean epsilonPresent = false;
            for (String s : firstSet.get(nonTerminals.get(i))) {
                if (s.equals("epsilon")) epsilonPresent = true;
                else concatenation.add(s);
            }

            if (epsilonPresent) i++;
            //1. FIRST(X) = FIRST(Y1)
            else
                break;
        }

        return concatenation;
    }

    public void first() {
        boolean firstSetChanged = true;

        for (String nonterminal : grammar.getNonTerminals()) {
            firstSet.put(nonterminal, new HashSet<>());

            Set<List<String>> productionForNonterminal = grammar.getProductionForNonterminal(nonterminal);
            for (List<String> production : productionForNonterminal) {
                if (grammar.getTerminals().contains(production.get(0)) || production.get(0).equals("epsilon"))
                    firstSet.get(nonterminal).add(production.get(0));
            }
        }

        while (firstSetChanged) {
            firstSetChanged = false;
            HashMap<String, Set<String>> newFirstSet = new HashMap<>();

            for (String nonterminal : grammar.getNonTerminals()) {
                Set<List<String>> productionForNonterminal = grammar.getProductionForNonterminal(nonterminal);
                Set<String> firstSetForNonTerminal = new HashSet<>(firstSet.get(nonterminal));

                for (List<String> production : productionForNonterminal) {
                    List<String> rhsNonTerminals = new ArrayList<>();
                    String rhsTerminal = null;

                    for (String symbol : production) {
                        if (this.grammar.getNonTerminals().contains(symbol))
                            rhsNonTerminals.add(symbol);
                        else {
                            rhsTerminal = symbol;
                            break;
                        }
                    }

                    firstSetForNonTerminal.addAll(concatenate(rhsNonTerminals, rhsTerminal));
                }
                if (!firstSetForNonTerminal.equals(firstSet.get(nonterminal))) {
                    firstSetChanged = true;
                }

                newFirstSet.put(nonterminal, firstSetForNonTerminal);
            }
            firstSet = newFirstSet;
        }
    }

    public void follow() {

        for (String nonterminal : grammar.getNonTerminals())
            followSet.put(nonterminal, new HashSet<>());

        // 1) FOLLOW(S) = { $ }
        followSet.get(grammar.getStartSymbol()).add("$");

        var followSetChanged = true;

        while (followSetChanged) {
            followSetChanged = false;
            HashMap<String, Set<String>> newFollowSet = new HashMap<>();

            for (String nonterminal : grammar.getNonTerminals()) {

                newFollowSet.put(nonterminal, new HashSet<>());

                HashMap<String, Set<List<String>>> rhsProductions = new HashMap<>();
                HashMap<String, Set<List<String>>> allProductions = grammar.getProductions();

                allProductions.forEach((nt, prods) -> {
                    for (var individualProduction : prods) {
                        if (individualProduction.contains(nonterminal)) {
                            if (!rhsProductions.containsKey(nt))
                                rhsProductions.put(nt, new HashSet<>());
                            rhsProductions.get(nt).add(individualProduction);
                        }
                    }
                });

                var followSetForNT = new HashSet<>(followSet.get(nonterminal));

                rhsProductions.forEach((nt, prods) -> {

                    for (var production : prods) {

                        ArrayList<String> oneProduction = (ArrayList<String>) production;

                        var nonterminalIndex = 0;
                        while(nonterminalIndex < oneProduction.size()) {
                            if (oneProduction.get(nonterminalIndex).equals(nonterminal)) {
                                // 3) If A->pB is a production, then everything in FOLLOW(A) is in FOLLOW(B).
                                if (nonterminalIndex + 1 == oneProduction.size()) {
                                    followSetForNT.addAll(followSet.get(nt));
                                }

                                else {
                                    var nextSymbol = oneProduction.get(nonterminalIndex + 1);

                                    if (grammar.getTerminals().contains(nextSymbol))
                                        followSetForNT.add(nextSymbol);

                                    else {
                                        // 4) If A->pBq is a production and FIRST(q) contains Є,
                                        //   then FOLLOW(B) contains { FIRST(q) – Є } U FOLLOW(A)
                                        if(firstSet.get(nextSymbol).contains("epsilon"))
                                            followSetForNT.addAll(followSet.get(nt));
                                        // 2) If A -> pBq is a production, where p, B and q are any grammar symbols,
                                        //  then everything in FIRST(q)  except Є is in FOLLOW(B).
                                        followSetForNT.addAll(firstSet.get(nextSymbol));
                                    }
                                }

                            }
                            nonterminalIndex++;
                        }

                    }

                });

                followSetForNT.remove("epsilon");

                if (!followSetForNT.equals(followSet.get(nonterminal)))
                    followSetChanged = true;

                newFollowSet.put(nonterminal, followSetForNT);
            }

            followSet = newFollowSet;
        }
    }

    public void generateParseTable() {
        List<String> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();

        rows.addAll(grammar.getNonTerminals());
        rows.addAll(grammar.getTerminals());

        columns.addAll(grammar.getTerminals());

        rows.add("$");
        columns.add("$");

        for(String row : rows) {
            for (String column : columns) {
                Pair<String, String> key = new Pair<>(row, column);
                Pair<String, Integer> value = new Pair<>("err", ERROR_CODE);
                this.parseTable.put(key, value);
            }
        }

        for(String column : columns) {
            Pair<String, String> key = new Pair<>(column, column);
            Pair<String, Integer> value = new Pair<>("pop", POP_CODE);
            this.parseTable.put(key, value);
        }

        parseTable.put(new Pair<String, String>("$", "$"), new Pair<String, Integer>("acc",-1));

        HashMap<String, Set<List<String>>> productions = grammar.getProductions();

        productions.forEach((nt, prods) -> {
            for(var prod : prods) {
                this.productionList.add(prod);
            }
        });


        productions.forEach((nonterminal, productionsForNonTerminal) -> {

            // For each production A –> α. (A tends to alpha)
            // 1. Find First(α) and for each terminal in First(α), make entry A –> α in the table.
            for(List<String> production : productionsForNonTerminal) {
                Set<String> firstSetForProduction = findFirstForProduction(production);

                for(String symbol : firstSetForProduction) {
                    if(!symbol.equals("epsilon")) {

                        if (parseTable.get(new Pair<>(nonterminal, symbol)).getFirst().equals("err"))
                            parseTable.put(new Pair<>(nonterminal, symbol), new Pair<>(String.join(" ", production), productionList.indexOf(production)+1));
                        else {
                            try {
                                throw new IllegalAccessException("CONFLICT: Pair " + nonterminal + "," + symbol);
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                }

                // If First(α) contains ε (epsilon) as terminal,
                // then find the Follow(A) and for each terminal in Follow(A), make entry A –>  ε in the table.
                if(firstSetForProduction.contains("epsilon")) {
                    for(String symbol : this.followSet.get(nonterminal)) {

                        if (parseTable.get(new Pair<>(nonterminal, symbol)).getFirst().equals("err"))
                            parseTable.put(new Pair<>(nonterminal, symbol), new Pair<>("epsilon", productionList.indexOf(production) + 1));
                        else {
                            try {
                                throw new IllegalAccessException("CONFLICT: Pair " + nonterminal + ", " + symbol);
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                }

            }
        });

    }

    // find first set for a given production (alpha)
    public Set<String> findFirstForProduction(List<String> alphaList) {
        Set<String> result = new HashSet<>();

        if(alphaList.size() == 1 && alphaList.get(0).equals("epsilon")) {
            result.add("epsilon");
            return result;
        }

        for(int i = 0; i < alphaList.size(); i++) {
            if(this.grammar.getTerminals().contains(alphaList.get(i))) {
                result.add(alphaList.get(i));
                break;
            }
            else if(this.grammar.getNonTerminals().contains(alphaList.get(i))) {
                result.addAll(this.firstSet.get(alphaList.get(i)));

                if(!result.contains("epsilon"))
                    break;

                else { // epsilon was added
                    if(i != alphaList.size() - 1)
                        result.remove("epsilon");
                }
            }
        }

        //System.out.println("=== findFirstForProduction ===");
        //System.out.println(alphaList.toString() + " : " + result.toString());

        return result;
    }

    public List<Integer> parse(List<String> sequence) {
        Stack<String> alphaStack = new Stack<>();
        Stack<String> betaStack = new Stack<>();
        List<Integer> pi = new ArrayList<>();

        alphaStack.push("$");

        ListIterator<String> listIterator = sequence.listIterator(sequence.size());
        while (listIterator.hasPrevious()) {
            alphaStack.push(listIterator.previous());
        }

        betaStack.push("$");
        betaStack.push(this.grammar.getStartSymbol());

        while(!alphaStack.peek().equals("$") && !betaStack.peek().equals("$")) {
            String alphaTop = alphaStack.peek();
            String betaTop = betaStack.peek();

            Pair<String, String> ptKey = new Pair<>(betaTop, alphaTop);
            Pair<String, Integer> ptValue = this.parseTable.get(ptKey);

            if(ptValue.getFirst().equals("err")) {
                System.out.println("Parsing Syntax ERROR!");
                System.out.println("KEY: " + ptKey + " VALUE " + ptValue.toString());
                return pi;
            }

            else if(ptValue.getFirst().equals("pop")) {
                alphaStack.pop();
                betaStack.pop();
            }

            else {
                betaStack.pop();

                if(!ptValue.getFirst().equals("epsilon")) {
                    String[] newProduction = ptValue.getFirst().split(" ");
                    for(int i = newProduction.length - 1; i >= 0; i--)
                        betaStack.push(newProduction[i]);
                }
                pi.add(ptValue.getSecond());
            }
        }
        return pi;
    }

    public String printParseTable() {
        StringBuilder builder = new StringBuilder();
        builder.append("\n === PARSE TABLE BEGIN === \n");
        parseTable.forEach((k, v) -> {
            String key_first = (String) k.getKey();
            String key_second = (String) k.getValue();
            String value_first = (String) v.getKey();
            Integer value_second = (Integer) v.getValue();
            builder.append(key_first + "  " + key_second).append(" -> ").append(value_first + "  " + value_second.toString()).append("\n");
        });
        builder.append("\n === PARSE TABLE END === \n");
        return builder.toString();
    }

    public String printParseTableWithoutErrors() {
        StringBuilder builder = new StringBuilder();
        builder.append("=== PARSE TABLE WITHOUT ERRORS BEGIN === \n");
        parseTable.forEach((k, v) -> {
            String key_first = (String) k.getKey();
            String key_second = (String) k.getValue();
            String value_first = (String) v.getKey();
            Integer value_second = (Integer) v.getValue();

            if(value_second != -1)
                builder.append(key_first + "  " + key_second).append(" -> ").append(value_first + "  " + value_second.toString()).append("\n");
        });
        builder.append("=== PARSE TABLE WITHOUT ERRORS END === \n");
        return builder.toString();
    }

    public String printFirst() {
        StringBuilder builder = new StringBuilder();
        firstSet.forEach((k, v) -> {
            builder.append(k).append(": ").append(v).append("\n");
        });
        return builder.toString();
    }

    public String printFollow() {
        StringBuilder builder = new StringBuilder();
        followSet.forEach((k, v) -> {
            builder.append(k).append(": ").append(v).append("\n");
        });
        return builder.toString();
    }

    public String printProductionList() {
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < this.productionList.size(); i++) {
            builder.append(i + 1 + " - " + String.join(" ", this.productionList.get(i)) + "\n");
        }
        return builder.toString();
    }

    public Grammar getGrammar() {
        return grammar;
    }
}