package com.embabel.guide;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.chat.Chatbot;
import com.embabel.chat.agent.AgentProcessChatbot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Create a chatbot backed by all actions on the AgentPlatform
 * that respond to UserMessages. Will use utility planning.
 */
@Configuration
class ChatConfig {

    @Bean
    Chatbot chatbot(AgentPlatform agentPlatform) {
        return AgentProcessChatbot.utilityFromPlatform(agentPlatform);
    }
}
