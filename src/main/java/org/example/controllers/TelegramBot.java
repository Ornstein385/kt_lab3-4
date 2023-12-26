package org.example.controllers;

import lombok.Getter;
import org.example.config.TelegramConfig;
import org.example.dao.TelegramUserDao;
import org.example.models.TelegramUser;
import org.example.service.filesenders.TelegramFileSender;
import org.example.service.StickPickService;
import org.example.service.generators.CustomPropertiesGenerator;
import org.example.service.generators.MonochromePresetGenerator;
import org.example.service.generators.PhotoPresetGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaDocument;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


@Controller
public class TelegramBot extends TelegramLongPollingBot {

    @Getter
    private final TelegramConfig config;

    @Getter
    private final MessageSource messageSource;

    @Getter
    private final TelegramUserDao telegramUserDao;

    @Getter
    private final StickPickService stickPickService;

    @Getter
    private final Set<String> validFormats = new TreeSet<>(Arrays.asList("A0", "A1", "A2", "A3", "A4"));

    public String getValidFormatsCommands() {
        StringBuilder b = new StringBuilder();
        for (String s : validFormats) {
            b.append("/").append(s).append(" ");
        }
        return b.toString();
    }

    @Getter
    @Value("${filepath}")
    private String filepath;

    @Autowired
    public TelegramBot(MessageSource messageSource, TelegramConfig config, TelegramUserDao telegramUserDao, StickPickService stickPickService) {
        super(config.getToken());
        this.messageSource = messageSource;
        this.config = config;
        this.telegramUserDao = telegramUserDao;
        this.stickPickService = stickPickService;
        Locale.setDefault(Locale.ENGLISH);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update != null) {
            TelegramUser telegramUser;
            Long updateUserId = null;
            if (update.hasMessage()) {
                updateUserId = update.getMessage().getFrom().getId();
            } else if (update.hasCallbackQuery()) {
                updateUserId = update.getCallbackQuery().getFrom().getId();
            }
            if (updateUserId == null) {
                return;
            }
            if (telegramUserDao.existsByTelegramUserId(updateUserId)) {
                telegramUser = telegramUserDao.getByTelegramUserId(updateUserId);
            } else {
                telegramUser = new TelegramUser();
                telegramUser.setTelegramUserId(updateUserId);
                telegramUser.setLocale(update.getMessage().getFrom().getLanguageCode());
                if (update.getMessage().getFrom().getUserName() != null) {
                    telegramUser.setTelegramUserName(update.getMessage().getFrom().getUserName());
                }
                telegramUserDao.update(telegramUser);
            }

            String locale = telegramUser.getLocale();
            Locale l = new Locale(locale);

            if (update.hasCallbackQuery()) {
                switch (update.getCallbackQuery().getData()) {
                    case "lang":
                        sendLangSettings(update.getCallbackQuery().getMessage().getChatId(), locale);
                        break;
                    case "instruction":
                        sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("instruction.message", null, l));
                        break;
                    case "photoPreset":
                        photoPresetProcessing(telegramUser, new Locale(locale));
                        break;
                    case "monochromePreset":
                        monochromePresetProcessing(telegramUser, new Locale(locale));
                        break;
                    case "customProperties":
                        customPropertiesProcessing(telegramUser, new Locale(locale));
                        break;
                    case "en":
                    case "ru":
                        telegramUser.setLocale(update.getCallbackQuery().getData());
                        telegramUserDao.update(telegramUser);
                        sendStartMassage(update.getCallbackQuery().getMessage().getChatId(), telegramUser.getLocale());
                        break;
                }
            }
            if (update.hasMessage()) {
                if (update.getMessage().hasText()) {
                    switch (update.getMessage().getText()) {
                        case "/start":
                            sendStartMassage(telegramUser.getTelegramUserId(), locale);
                            break;
                        case "/instruction":
                            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("instruction.message", null, l));
                            break;
                        case "/lang":
                            sendLangSettings(telegramUser.getTelegramUserId(), locale);
                            break;
                        case "/settings":
                            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("properties.message", null, l));
                            break;
                        case "/A0":
                        case "/A1":
                        case "/A2":
                        case "/A3":
                        case "/A4":
                            telegramUser.setSheetFormat(update.getMessage().getText().substring(1));
                            telegramUserDao.update(telegramUser);
                            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("property.accept", null, l));
                            break;
                        case "/photo":
                            photoPresetProcessing(telegramUser, new Locale(locale));
                            break;
                        case "/mono":
                            monochromePresetProcessing(telegramUser, new Locale(locale));
                            break;
                        case "/custom":
                            customPropertiesProcessing(telegramUser, new Locale(locale));
                    }
                    if (update.getMessage().getText().startsWith("format=")) {
                        int index = update.getMessage().getText().indexOf('=');
                        String format = update.getMessage().getText().substring(index + 1).toUpperCase();
                        if (validFormats.contains(format)) {
                            telegramUser.setSheetFormat(format);
                            telegramUserDao.update(telegramUser);
                            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("property.accept", null, l));
                        } else {
                            sendSimpleMassage(telegramUser.getTelegramUserId(),
                                    messageSource.getMessage("format.reject", null, l) + " " + getValidFormatsCommands());
                        }
                    }
                    if (update.getMessage().getText().startsWith("denoising=")) {
                        int index = update.getMessage().getText().indexOf('=');
                        String s = update.getMessage().getText().substring(index + 1).toUpperCase();
                        try {
                            int value = Integer.parseInt(s);
                            telegramUser.setSmallDetailsRemover(value);
                            telegramUserDao.update(telegramUser);
                            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("property.accept", null, l));
                        } catch (NumberFormatException e) {
                            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("denoising.reject", null, l));
                        }
                    }
                    if (update.getMessage().getText().startsWith("brightness=")) {
                        int index = update.getMessage().getText().indexOf('=');
                        String s = update.getMessage().getText().substring(index + 1).toUpperCase();
                        try {
                            int value = Integer.parseInt(s);
                            if (value >= 0 && value <= 255) {
                                telegramUser.setBrightnessLevel(value);
                                telegramUserDao.update(telegramUser);
                                sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("property.accept", null, l));
                            } else {
                                sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("brightness.reject", null, l));
                            }
                        } catch (NumberFormatException e) {
                            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("brightness.reject", null, l));
                        }
                    }
                }
                if (update.getMessage().hasPhoto()) {
                    var photos = update.getMessage().getPhoto();
                    if (photos.isEmpty()) {
                        return;
                    }
                    String fileId = photos.stream().max(Comparator.comparing(PhotoSize::getFileSize)).get().getFileId();
                    telegramUser.setFileId(fileId);
                    telegramUserDao.update(telegramUser);
                    sendDefaultArtSettings(update.getMessage().getChatId(), locale);
                }
                if (update.getMessage().hasDocument()) {
                    var documents = update.getMessage().getDocument();
                    String fileId = documents.getFileId();
                    if (isImageFile(fileId)) {
                        telegramUser.setFileId(fileId);
                        telegramUserDao.update(telegramUser);
                        sendDefaultArtSettings(update.getMessage().getChatId(), locale);
                    } else {
                        sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("no.photo.file.reject", null, l));
                    }
                }
            }
        }
    }

    @Override
    public String getBotUsername() {
        return config.getName();
    }

    private void photoPresetProcessing(TelegramUser telegramUser, Locale locale) {
        if (telegramUser.getSheetFormat() == null){
            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("no.found.format.reject", null, locale)
                    + " " + getValidFormatsCommands());
            return;
        }
        BufferedImage image = getImageFromFileId(telegramUser.getFileId());
        if (image != null) {
            String folderName = (telegramUser.getTelegramUserName() != null ?
                    telegramUser.getTelegramUserName() + "_" : "id" + telegramUser.getTelegramUserId() + "_") +
                    Long.toHexString(System.currentTimeMillis());
            if (stickPickService.add(new PhotoPresetGenerator(image, telegramUser.getSheetFormat(), filepath, folderName),
                    new TelegramFileSender(this, telegramUser,
                            Arrays.asList(Paths.get(filepath + "\\" + folderName + "\\result.pdf"),
                                    Paths.get(filepath + "\\" + folderName + "\\preview.pdf"))))) {
                sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("generating.start", null, locale));
            } else {
                sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("queue.overflow", null, locale));
            }
        } else {
            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("missing.file.reject", null, locale));
        }
    }

    private void monochromePresetProcessing(TelegramUser telegramUser, Locale locale) {
        if (telegramUser.getSheetFormat() == null){
            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("no.found.format.reject", null, locale)
                    + " " + getValidFormatsCommands());
            return;
        }
        BufferedImage image = getImageFromFileId(telegramUser.getFileId());
        if (image != null) {
            String folderName = (telegramUser.getTelegramUserName() != null ?
                    telegramUser.getTelegramUserName() + "_" : "id" + telegramUser.getTelegramUserId() + "_") +
                    Long.toHexString(System.currentTimeMillis());
            if (stickPickService.add(new MonochromePresetGenerator(image, telegramUser.getSheetFormat(), filepath, folderName),
                    new TelegramFileSender(this, telegramUser,
                            Arrays.asList(Paths.get(filepath + "\\" + folderName + "\\result.pdf"),
                                    Paths.get(filepath + "\\" + folderName + "\\preview.pdf"))))) {
                sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("generating.start", null, locale));
            } else {
                sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("queue.overflow", null, locale));
            }
        } else {
            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("missing.file.reject", null, locale));
        }
    }

    private void customPropertiesProcessing(TelegramUser telegramUser, Locale locale) {
        if (telegramUser.getSheetFormat() == null){
            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("no.found.format.reject", null, locale)
                    + " " + getValidFormatsCommands());
            return;
        }
        if (telegramUser.getBrightnessLevel() == null || telegramUser.getSmallDetailsRemover() == null){    //ОБЯЗАТЕЛЬНЫЕ НАСТРОЙКИ
            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("no.found.custom.properties.reject", null, locale));
            return;
        }
        BufferedImage image = getImageFromFileId(telegramUser.getFileId());
        if (image != null) {
            String folderName = (telegramUser.getTelegramUserName() != null ?
                    telegramUser.getTelegramUserName() + "_" : "id" + telegramUser.getTelegramUserId() + "_") +
                    Long.toHexString(System.currentTimeMillis());
            if (stickPickService.add(new CustomPropertiesGenerator(image, telegramUser.getSheetFormat(), filepath, folderName,
                            telegramUser.getSmallDetailsRemover() != null ? telegramUser.getSmallDetailsRemover() : 128,
                            telegramUser.getBrightnessLevel() != null ? telegramUser.getBrightnessLevel() : 100),
                    new TelegramFileSender(this, telegramUser,
                            Arrays.asList(Paths.get(filepath + "\\" + folderName + "\\result.pdf"),
                                    Paths.get(filepath + "\\" + folderName + "\\preview.pdf"))))) {
                sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("generating.start", null, locale));
            } else {
                sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("queue.overflow", null, locale));
            }
        } else {
            sendSimpleMassage(telegramUser.getTelegramUserId(), messageSource.getMessage("missing.file.reject", null, locale));
        }
    }

    public void sendSimpleMassage(Long chatId, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
        }
    }

    private void sendStartMassage(Long chatId, String locale) {
        Locale l = new Locale(locale);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageSource.getMessage("start.message", null, l));

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline1 = new ArrayList<>();

        InlineKeyboardButton inlineKeyboardButton1 = new InlineKeyboardButton();
        inlineKeyboardButton1.setText(messageSource.getMessage("lang.button", null, l));
        inlineKeyboardButton1.setCallbackData("lang");

        InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
        inlineKeyboardButton2.setText(messageSource.getMessage("instruction.button", null, l));
        inlineKeyboardButton2.setCallbackData("instruction");

        rowInline1.add(inlineKeyboardButton1);
        rowInline1.add(inlineKeyboardButton2);
        rowsInline.add(rowInline1);
        inlineKeyboardMarkup.setKeyboard(rowsInline);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
        }
    }

    private void sendLangSettings(Long chatId, String locale) {
        Locale l = new Locale(locale);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageSource.getMessage("lang.message", null, l));

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline1 = new ArrayList<>();

        InlineKeyboardButton inlineKeyboardButton1 = new InlineKeyboardButton();
        inlineKeyboardButton1.setText(messageSource.getMessage("lang.en", null, l));
        inlineKeyboardButton1.setCallbackData("en");

        InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
        inlineKeyboardButton2.setText(messageSource.getMessage("lang.ru", null, l));
        inlineKeyboardButton2.setCallbackData("ru");

        rowInline1.add(inlineKeyboardButton1);
        rowInline1.add(inlineKeyboardButton2);
        rowsInline.add(rowInline1);
        inlineKeyboardMarkup.setKeyboard(rowsInline);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
        }
    }

    private void sendDefaultArtSettings(Long chatId, String locale) {
        Locale l = new Locale(locale);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageSource.getMessage("choose.preset.message", null, l) + " " + getValidFormatsCommands() + "\n\n" +
                messageSource.getMessage("custom.preset.message", null, l));

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        InlineKeyboardButton inlineKeyboardButton1 = new InlineKeyboardButton();
        inlineKeyboardButton1.setText(messageSource.getMessage("photo.preset.button", null, l));
        inlineKeyboardButton1.setCallbackData("photoPreset");
        rowsInline.add(Collections.singletonList(inlineKeyboardButton1)); // Добавляем кнопку в новом ряду

        InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
        inlineKeyboardButton2.setText(messageSource.getMessage("monochrome.preset.button", null, l));
        inlineKeyboardButton2.setCallbackData("monochromePreset");
        rowsInline.add(Collections.singletonList(inlineKeyboardButton2)); // Добавляем кнопку в новом ряду

        InlineKeyboardButton inlineKeyboardButton3 = new InlineKeyboardButton();
        inlineKeyboardButton3.setText(messageSource.getMessage("custom.properties.button", null, l));
        inlineKeyboardButton3.setCallbackData("customProperties");
        rowsInline.add(Collections.singletonList(inlineKeyboardButton3)); // Добавляем кнопку в новом ряду

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace(); // Не игнорируем исключение, а печатаем стек вызова для диагностики
        }
    }

    /*
    private void sendCustomArtSettings(Long chatId, String locale) {
        Locale l = new Locale(locale);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageSource.getMessage("custom.properties.message", null, l));

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline1 = new ArrayList<>();

        InlineKeyboardButton inlineKeyboardButton1 = new InlineKeyboardButton();
        inlineKeyboardButton1.setText("A4");
        inlineKeyboardButton1.setCallbackData("A4");

        InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();
        inlineKeyboardButton2.setText("A3");
        inlineKeyboardButton2.setCallbackData("A3");

        InlineKeyboardButton inlineKeyboardButton3 = new InlineKeyboardButton();
        inlineKeyboardButton3.setText("A2");
        inlineKeyboardButton3.setCallbackData("A2");

        InlineKeyboardButton inlineKeyboardButton4 = new InlineKeyboardButton();
        inlineKeyboardButton4.setText("A1");
        inlineKeyboardButton4.setCallbackData("A1");

        InlineKeyboardButton inlineKeyboardButton5 = new InlineKeyboardButton();
        inlineKeyboardButton5.setText("A0");
        inlineKeyboardButton5.setCallbackData("A0");

        rowInline1.add(inlineKeyboardButton1);
        rowInline1.add(inlineKeyboardButton2);
        rowInline1.add(inlineKeyboardButton3);
        rowInline1.add(inlineKeyboardButton4);
        rowInline1.add(inlineKeyboardButton5);
        rowsInline.add(rowInline1);
        inlineKeyboardMarkup.setKeyboard(rowsInline);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
        }
    }

     */

    public void sendImageGroup(Long chatId, List<Path> imagePaths) {
        SendMediaGroup sendMediaGroup = new SendMediaGroup();
        sendMediaGroup.setChatId(chatId);
        List<InputMedia> media = new ArrayList<>();
        for (Path imagePath : imagePaths) {
            InputMediaPhoto inputMediaPhoto = new InputMediaPhoto();
            inputMediaPhoto.setMedia(imagePath.toFile(), imagePath.getFileName().toString());
            media.add(inputMediaPhoto);
        }
        sendMediaGroup.setMedias(media);
        try {
            execute(sendMediaGroup);
        } catch (TelegramApiException e) {
        }
    }

    public void sendDocumentGroup(Long chatId, List<Path> documentPaths) {
        SendMediaGroup sendMediaGroup = new SendMediaGroup();
        sendMediaGroup.setChatId(chatId);
        List<InputMedia> media = new ArrayList<>();
        for (Path documentPath : documentPaths) {
            InputMediaDocument inputMediaDocument = new InputMediaDocument();
            inputMediaDocument.setMedia(documentPath.toFile(), documentPath.getFileName().toString());
            media.add(inputMediaDocument);
        }
        sendMediaGroup.setMedias(media);
        try {
            execute(sendMediaGroup);
        } catch (TelegramApiException e) {
        }
    }

    public BufferedImage getImageFromFileId(String fileId) {
        try {
            GetFile getFileRequest = new GetFile();
            getFileRequest.setFileId(fileId);
            File file = execute(getFileRequest);
            String fileUrl = getFileUrl(file.getFilePath());
            InputStream inputStream = new URL(fileUrl).openStream();
            return ImageIO.read(inputStream);
        } catch (TelegramApiException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isImageFile(String fileId) {
        try {
            GetFile getFileRequest = new GetFile();
            getFileRequest.setFileId(fileId);
            File file = execute(getFileRequest);
            String fileUrl = getFileUrl(file.getFilePath());
            InputStream inputStream = new URL(fileUrl).openStream();
            BufferedImage image = ImageIO.read(inputStream);
            return image != null;
        } catch (TelegramApiException | IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getFileUrl(String filePath) throws TelegramApiException {
        return "https://api.telegram.org/file/bot" + config.getToken() + "/" + filePath;
    }
}