package com.example.springboot.service;

import com.example.springboot.DTO.ArticleDTO;
import com.example.springboot.DTO.MessageDTO;
import com.example.springboot.entity.Article;
import com.example.springboot.entity.Category;
import com.example.springboot.entity.Message;
import com.example.springboot.entity.Users;
import com.example.springboot.repository.ArticleRepository;
import com.example.springboot.repository.CategoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.jms.JMSException;
import javax.persistence.EntityNotFoundException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import java.util.List;

@Service
@AllArgsConstructor
public class ArticleService {
    private final ArticleRepository articleRepository;
    private final CategoryRepository categoryRepository;
    private final UserService userService;
    private final MessageService messageService;
    private final UserTransaction userTransaction;
    private final MessageProducer messageProducer;

    @PreAuthorize("hasAuthority('users:write')")
    public Iterable<Article> getAllArticles() {
        return articleRepository.findAll();
    }

    @PreAuthorize("hasAuthority('users:read')")
    public Article getArticleById(long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(String.valueOf(id)));
    }

    @PreAuthorize("hasAuthority('users:read')")
    public List<Article> getAllPostedArticles() {
        return articleRepository.findByStatusTrue();
    }

    @PreAuthorize("hasAuthority('users:write')")
    public List<Article> getAllUncheckedArticles() {
        return articleRepository.findByStatusFalse();
    }

    @PreAuthorize("hasAuthority('users:read')")
    public Article newArticle (ArticleDTO articleDTO) throws ResponseStatusException, JMSException {

            Users user = userService.getById(articleDTO.author);
            MessageDTO messageDTO;

            if (!isCorrect(articleDTO.text)) {
                messageDTO = new MessageDTO("??????????????????, " + user.getLogin() + ". ???????? ???????????? " + articleDTO.name + "???????????????? ?????????????????????? ??????????", user.getId());
                messageService.newMessage(messageDTO,user.getLogin());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "?? ???????????? ???????????????????????? ?????????????????????? ??????????");
            }

            Article article = new Article();
            article.setAuthor(user);
            article.setText(articleDTO.text);
            article.setName(articleDTO.name);
            article.setStatus(articleDTO.status);
            Category category = categoryRepository.findById(articleDTO.category).
                    orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "?????????????????? ???? ??????????????"));;

            article.setCategory(category);

            messageDTO = new MessageDTO("??????????????????, " + user.getLogin() + ".???????? ???????????? " + articleDTO.name + "???????????? ????????????????. ???????????????? ???????????????????? ???????????????? ??????????????????????", user.getId());
            messageService.newMessage(messageDTO,user.getLogin());
            article = articleRepository.save(article);
            messageProducer.produceMessage(articleDTO);



            return article;


    }

    public boolean isCorrect(String text) {
        int count = -1;
        String[] words = {"GunDone", "ChopIsDish", "YourBunnyWrote", "PeaceDeath"};
        for (String word : words) {
            int indexWord = text.indexOf(word);
            if (indexWord != -1) count++;
        }
        return count == -1;
    }

    @PreAuthorize("hasAuthority('users:write')")
    public void deleteArticle(int id) {

        try {
            userTransaction.begin();
            Message message = new Message();
            Article article = articleRepository.findById((long) id)
                    .orElseThrow(() -> new EntityNotFoundException(String.valueOf(id)));

            deleteArticle(article);
            userTransaction.commit();
        } catch (Exception e) {
            try {
                userTransaction.rollback();
            } catch (SystemException systemException) {
                systemException.printStackTrace();
            }
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "?????? ???????????????????? ???????????????????? ???????????????? ????????????");
        }

    }

    public void deleteArticle(Article article) {
        articleRepository.delete(article);
    }
}