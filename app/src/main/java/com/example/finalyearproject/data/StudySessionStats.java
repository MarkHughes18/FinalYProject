package com.example.finalyearproject.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

public class StudySessionStats {

    private final ModeStats mcqStats = new ModeStats();
    private final ModeStats clozeStats = new ModeStats();
    private final ModeStats trueFalseStats = new ModeStats();
    private final ModeStats matchingStats = new ModeStats();
    private final ModeStats flashcardStats = new ModeStats();

    private final Set<Integer> answeredMcqPositions = new HashSet<>();
    private final Set<Integer> answeredClozePositions = new HashSet<>();
    private final Set<Integer> answeredTrueFalsePositions = new HashSet<>();
    private final Set<Integer> answeredMatchingPositions = new HashSet<>();

    private final Map<Integer, Integer> mcqSelectedAnswers = new HashMap<>();
    private final Map<Integer, Integer> clozeSelectedAnswers = new HashMap<>();
    private final Map<Integer, Boolean> trueFalseSelectedAnswers = new HashMap<>();

    public ModeStats getMcqStats() {
        return mcqStats;
    }

    public ModeStats getClozeStats() {
        return clozeStats;
    }

    public ModeStats getTrueFalseStats() {
        return trueFalseStats;
    }

    public ModeStats getMatchingStats() {
        return matchingStats;
    }

    public ModeStats getFlashcardStats() {return flashcardStats;}

    public boolean markMcqAnswered(int position){
        return answeredMcqPositions.add(position);
    }
    public boolean markClozeAnswered(int position){
        return answeredClozePositions.add(position);
    }
    public boolean markTrueFalseAnswered(int position){
        return answeredTrueFalsePositions.add(position);
    }
    public boolean markMatchingAnswered(int position){
        return answeredMatchingPositions.add(position);
    }

    public boolean isMcqAnswered(int position) {
        return answeredMcqPositions.contains(position);
    }

    public boolean isClozeAnswered(int position) {
        return answeredClozePositions.contains(position);
    }

    public boolean isTrueFalseAnswered(int position) {
        return answeredTrueFalsePositions.contains(position);
    }

    public boolean isMatchingAnswered(int position) {
        return answeredMatchingPositions.contains(position);
    }

    public Set<Integer> getAnsweredMcqPositions(){
        return answeredMcqPositions;
    }

    public Set<Integer> getAnsweredClozePositions(){
        return answeredClozePositions;
    }

    public Set<Integer> getAnsweredTrueFalsePositions(){
        return answeredTrueFalsePositions;
    }

    public Set<Integer> getAnsweredMatchingPositions(){
        return answeredMatchingPositions;
    }

    public void setMcqAnswer(int position, int selectedIndex) {
        mcqSelectedAnswers.put(position, selectedIndex);
    }

    public Integer getMcqAnswer(int position) {
        return mcqSelectedAnswers.get(position);
    }

    public java.util.Map<Integer, Integer> getMcqSelectedAnswers() {
        return mcqSelectedAnswers;
    }

    public void setClozeAnswer(int position, int selectedIndex) {
        clozeSelectedAnswers.put(position, selectedIndex);
    }

    public Integer getClozeAnswer(int position) {
        return clozeSelectedAnswers.get(position);
    }

    public java.util.Map<Integer, Integer> getClozeSelectedAnswers() {
        return clozeSelectedAnswers;
    }

    public void setTrueFalseAnswer(int position, boolean answer) {
        trueFalseSelectedAnswers.put(position, answer);
    }

    public Boolean getTrueFalseAnswer(int position) {
        return trueFalseSelectedAnswers.get(position);
    }

    public java.util.Map<Integer, Boolean> getTrueFalseSelectedAnswers() {
        return trueFalseSelectedAnswers;
    }
    public int getTotalAttempted() {
        return flashcardStats.getAttempted()
                + mcqStats.getAttempted()
                + clozeStats.getAttempted()
                + trueFalseStats.getAttempted()
                + matchingStats.getAttempted();
    }

    public int getTotalCorrect() {
        return mcqStats.getCorrect()
                + clozeStats.getCorrect()
                + trueFalseStats.getCorrect()
                + matchingStats.getCorrect();
    }

    public int getTotalIncorrect() {
        return mcqStats.getIncorrect()
                + clozeStats.getIncorrect()
                + trueFalseStats.getIncorrect()
                + matchingStats.getIncorrect();
    }

    public int getOverallAccuracyPercent() {
        int attempted = getTotalCorrect() + getTotalIncorrect();
        if (attempted == 0) {
            return 0;
        }
        return (int) Math.round((getTotalCorrect() * 100.0) / attempted);
    }
}