(function () {
    var DEFAULT_TEXT_LIMIT = 5000;
    var DEFAULT_HTML_LIMIT = 20000;
    var DEFAULT_FILE_LIMIT = 10 * 1024 * 1024;
    var DEFAULT_IMAGE_LIMIT = 5 * 1024 * 1024;

    window.initializeBoardForm = function (options) {
        var config = Object.assign({
            formSelector: '.board-write-form',
            titleSelector: '#title',
            contentSelector: '#content',
            submitButtonSelector: '#submitBtn',
            fileInputSelector: 'input[name="files"]',
            submittedText: '저장 중...',
            requireTitle: true,
            useSummernote: true,
            maxTextLength: DEFAULT_TEXT_LIMIT,
            maxHtmlLength: DEFAULT_HTML_LIMIT,
            maxFileSize: DEFAULT_FILE_LIMIT,
            maxImageSize: DEFAULT_IMAGE_LIMIT
        }, options || {});

        var form = document.querySelector(config.formSelector);
        var titleInput = document.querySelector(config.titleSelector);
        var contentInput = document.querySelector(config.contentSelector);
        var submitButton = document.querySelector(config.submitButtonSelector);
        var csrfTokenInput = document.querySelector('input[name="_csrf"]');
        var csrfToken = csrfTokenInput ? csrfTokenInput.value : '';
        var isSubmitting = false;

        if (!form || !contentInput) {
            return;
        }

        if (config.useSummernote) {
            initializeEditorWhenReady(config, csrfToken);
        }

        form.addEventListener('submit', function (event) {
            var content = getEditorContent(config);
            contentInput.value = content;
            var plainText = stripHtml(content).trim();
            var textLength = countCharacters(plainText);
            var fileInput = form.querySelector(config.fileInputSelector);

            if (config.requireTitle && titleInput && titleInput.value.trim() === '') {
                event.preventDefault();
                alert('제목을 입력해주세요.');
                titleInput.focus();
                return;
            }

            if (plainText === '') {
                event.preventDefault();
                alert('내용을 입력해주세요.');
                focusContent(config, contentInput);
                return;
            }

            if (textLength > config.maxTextLength) {
                event.preventDefault();
                alert('본문은 ' + config.maxTextLength.toLocaleString()
                    + '자 이하로 입력해주세요. 현재 ' + textLength.toLocaleString() + '자입니다.');
                focusContent(config, contentInput);
                return;
            }

            if (content.length > config.maxHtmlLength) {
                event.preventDefault();
                alert('본문 서식 정보를 포함해 ' + config.maxHtmlLength.toLocaleString()
                    + '자 이하로 입력해주세요. 현재 ' + content.length.toLocaleString() + '자입니다.');
                focusContent(config, contentInput);
                return;
            }

            if (hasOversizedFile(fileInput, config.maxFileSize)) {
                event.preventDefault();
                alert('첨부파일은 10MB 이하만 업로드할 수 있습니다.');
                return;
            }

            if (isSubmitting) {
                event.preventDefault();
                return;
            }

            isSubmitting = true;
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.textContent = config.submittedText;
            }
        });
    };

    window.initializeBoardReplyForm = function (options) {
        var config = Object.assign({
            formSelector: '.board-reply-form',
            contentSelector: '#replyContent',
            submitButtonSelector: '.board-reply-form button[type="submit"]',
            submittedText: '답변 등록 중...',
            maxTextLength: DEFAULT_TEXT_LIMIT,
            maxHtmlLength: DEFAULT_HTML_LIMIT
        }, options || {});

        window.initializeBoardForm(Object.assign(config, {
            titleSelector: '#replyTitle',
            fileInputSelector: '__none__',
            requireTitle: false,
            useSummernote: false
        }));
    };

    function initializeEditorWhenReady(config, csrfToken, attempt) {
        var retry = attempt || 0;

        if (window.jQuery && $.fn && $.fn.summernote) {
            var summernoteOptions = {
                height: 400,
                minHeight: 200,
                maxHeight: 600,
                placeholder: '내용을 입력하세요',
                toolbar: [
                    ['style', ['bold', 'italic', 'underline', 'strikethrough', 'clear']],
                    ['para', ['ul', 'ol']],
                    ['insert', ['picture', 'link']],
                    ['view', ['codeview']]
                ],
                colors: [
                    ['#111111', '#444444', '#7a6f5d', '#8b5e3c', '#c9a84c', '#d97706', '#dc2626', '#2563eb'],
                    ['#ffffff', '#f8f5ee', '#efe1bb', '#fde68a', '#fecaca', '#bbf7d0', '#bfdbfe', '#ddd6fe']
                ],
                colorsName: [
                    ['Black', 'Dark Gray', 'Warm Gray', 'Brown', 'Gold', 'Orange', 'Red', 'Blue'],
                    ['White', 'Ivory', 'Beige', 'Yellow', 'Pink', 'Green', 'Sky', 'Purple']
                ],
                fontSizes: ['10', '12', '14', '16', '18', '24', '32', '48'],
                fontSizeUnits: ['px'],
                dialogsInBody: true,
                callbacks: {
                    onImageUpload: function (files) {
                        for (var i = 0; i < files.length; i++) {
                            uploadEditorImage(files[i], csrfToken, config.maxImageSize, config.contentSelector);
                        }
                    }
                }
            };

            if ($.summernote && $.summernote.lang && $.summernote.lang['ko-KR']) {
                summernoteOptions.lang = 'ko-KR';
            }

            $(config.contentSelector).summernote(summernoteOptions);
            if (window.mountBoardEditorControls) {
                window.mountBoardEditorControls(config.contentSelector);
            }
            return;
        }

        if (retry < 20) {
            window.setTimeout(function () {
                initializeEditorWhenReady(config, csrfToken, retry + 1);
            }, 250);
            return;
        }

        alert('에디터를 불러오지 못했습니다. 새로고침 후 다시 시도해주세요.');
    }

    function getEditorContent(config) {
        if (config.useSummernote && window.jQuery && $.fn && $.fn.summernote) {
            return $(config.contentSelector).summernote('code');
        }

        var contentInput = document.querySelector(config.contentSelector);
        return contentInput ? contentInput.value : '';
    }

    function focusContent(config, contentInput) {
        if (config.useSummernote && window.jQuery && $.fn && $.fn.summernote) {
            $(config.contentSelector).summernote('focus');
            return;
        }
        contentInput.focus();
    }

    function stripHtml(html) {
        var tempDiv = document.createElement('div');
        tempDiv.innerHTML = html || '';
        return tempDiv.textContent || tempDiv.innerText || '';
    }

    function countCharacters(text) {
        return Array.from(text || '').length;
    }

    function hasOversizedFile(input, maxFileSize) {
        if (!input || !input.files) {
            return false;
        }

        for (var i = 0; i < input.files.length; i++) {
            if (input.files[i].size > maxFileSize) {
                return true;
            }
        }
        return false;
    }

    async function uploadEditorImage(file, csrfToken, maxImageSize, contentSelector) {
        if (!file) {
            return;
        }

        if (file.size > maxImageSize) {
            alert('에디터 이미지는 5MB 이하만 업로드할 수 있습니다.');
            return;
        }

        var formData = new FormData();
        formData.append('file', file);

        try {
            var response = await fetch('/files/images', {
                method: 'POST',
                headers: { 'X-CSRF-TOKEN': csrfToken },
                body: formData
            });

            if (!response.ok) {
                var message = await response.text();
                throw new Error(message || '이미지 업로드에 실패했습니다.');
            }

            var data = await response.json();
            if (!data || !data.url) {
                throw new Error('이미지 주소를 받지 못했습니다.');
            }

            $(contentSelector).summernote('insertImage', data.url, function ($image) {
                if (data.originalFilename) {
                    $image.attr('data-original-name', data.originalFilename);
                    $image.attr('alt', data.originalFilename);
                }
            });
        } catch (error) {
            alert(error.message || '이미지 업로드에 실패했습니다.');
            console.error('Image upload failed:', error);
        }
    }
})();
