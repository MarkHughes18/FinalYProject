package com.example.finalyearproject.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.StudyPackResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class McqAdapter extends RecyclerView.Adapter<McqAdapter.ViewHolder> {

    private final List<StudyPackResponse.McqQuestion> items;
    // position -> selected option index
    private final Map<Integer, Integer> selectedAnswers = new HashMap<>();

    public McqAdapter(List<StudyPackResponse.McqQuestion> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mcq, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StudyPackResponse.McqQuestion item = items.get(position);

        holder.questionTv.setText(item.question);

        bindOptionButton(holder.option1Btn, item, 0, position);
        bindOptionButton(holder.option2Btn, item, 1, position);
        bindOptionButton(holder.option3Btn, item, 2, position);
        bindOptionButton(holder.option4Btn, item, 3, position);

        boolean alreadyAnswered = selectedAnswers.containsKey(position);

        if (alreadyAnswered) {
            int selectedIndex = selectedAnswers.get(position);
            boolean correct = selectedIndex == item.correctIndex;

            holder.resultTv.setVisibility(View.VISIBLE);
            holder.answerTv.setVisibility(View.VISIBLE);
            holder.explanationTv.setVisibility(View.VISIBLE);

            holder.resultTv.setText(correct ? "Correct" : "Incorrect");

            String correctAnswer = getOptionText(item, item.correctIndex);
            holder.answerTv.setText("Correct answer: " + correctAnswer);
            holder.explanationTv.setText(item.explanation != null ? item.explanation : "");

            setButtonsEnabled(holder, false);
        } else {
            holder.resultTv.setVisibility(View.GONE);
            holder.answerTv.setVisibility(View.GONE);
            holder.explanationTv.setVisibility(View.GONE);
            setButtonsEnabled(holder, true);
        }
    }

    private void bindOptionButton(Button button,
                                  StudyPackResponse.McqQuestion item,
                                  int optionIndex,
                                  int position) {

        String text = getOptionText(item, optionIndex);

        if (text == null || text.isBlank()) {
            button.setVisibility(View.GONE);
            button.setOnClickListener(null);
            return;
        }

        button.setVisibility(View.VISIBLE);
        button.setText(text);

        if (selectedAnswers.containsKey(position)) {
            button.setOnClickListener(null);
            return;
        }

        button.setOnClickListener(v -> {
            selectedAnswers.put(position, optionIndex);
            notifyItemChanged(position);
        });
    }

    private String getOptionText(StudyPackResponse.McqQuestion item, int index) {
        if (item.options == null || index < 0 || index >= item.options.size()) {
            return null;
        }
        return item.options.get(index);
    }

    private void setButtonsEnabled(ViewHolder holder, boolean enabled) {
        holder.option1Btn.setEnabled(enabled);
        holder.option2Btn.setEnabled(enabled);
        holder.option3Btn.setEnabled(enabled);
        holder.option4Btn.setEnabled(enabled);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView questionTv;
        Button option1Btn;
        Button option2Btn;
        Button option3Btn;
        Button option4Btn;
        TextView resultTv;
        TextView answerTv;
        TextView explanationTv;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            questionTv = itemView.findViewById(R.id.mcqQuestionTV);
            option1Btn = itemView.findViewById(R.id.mcqOption1Btn);
            option2Btn = itemView.findViewById(R.id.mcqOption2Btn);
            option3Btn = itemView.findViewById(R.id.mcqOption3Btn);
            option4Btn = itemView.findViewById(R.id.mcqOption4Btn);
            resultTv = itemView.findViewById(R.id.mcqResultTV);
            answerTv = itemView.findViewById(R.id.mcqAnswerTV);
            explanationTv = itemView.findViewById(R.id.mcqExplanationTV);
        }
    }
}