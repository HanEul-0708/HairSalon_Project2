// board.js — 게시판 전용 자바스크립트
// =====================================================

document.addEventListener('DOMContentLoaded', function () {
    bindFileInputs();
    bindSearchForm();
    bindFileDropZones();
    bindDeleteCheckboxSummary();
});

function bindFileInputs() {
    var fileInputs = document.querySelectorAll('input[type="file"]');
    fileInputs.forEach(function (input) {
        input.addEventListener('change', function () {
            updateFileInfo(this);
            renderFilePreview(this);
        });
    });
}

function bindSearchForm() {
    var searchForm = document.querySelector('.board-search-form');
    var searchInput = document.querySelector('.board-search-input');
    if (searchForm && searchInput) {
        searchInput.addEventListener('keypress', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                searchForm.submit();
            }
        });
    }
}

function bindFileDropZones() {
    var zones = document.querySelectorAll('[data-file-dropzone]');
    zones.forEach(function (zone) {
        var input = zone.querySelector('input[type="file"]');
        if (!input) return;

        ['dragenter', 'dragover'].forEach(function (eventName) {
            zone.addEventListener(eventName, function (event) {
                event.preventDefault();
                zone.classList.add('is-dragover');
            });
        });

        ['dragleave', 'drop'].forEach(function (eventName) {
            zone.addEventListener(eventName, function (event) {
                event.preventDefault();
                zone.classList.remove('is-dragover');
            });
        });

        zone.addEventListener('drop', function (event) {
            if (!event.dataTransfer || !event.dataTransfer.files) return;
            input.files = event.dataTransfer.files;
            updateFileInfo(input);
            renderFilePreview(input);
        });
    });
}

function bindDeleteCheckboxSummary() {
    var deleteCheckboxes = document.querySelectorAll('input[name="deleteFileIds"]');
    if (!deleteCheckboxes.length) return;

    deleteCheckboxes.forEach(function (checkbox) {
        checkbox.addEventListener('change', function () {
            var li = checkbox.closest('li');
            if (li) {
                li.classList.toggle('is-delete-checked', checkbox.checked);
            }
        });
    });
}

function updateFileInfo(input) {
    var infoText = input.closest('.form-group') && input.closest('.form-group').querySelector('.form-text');
    if (!infoText) return;
    var files = Array.from(input.files || []);
    if (!files.length) {
        infoText.textContent = '파일은 최대 10MB, 여러 개 선택 가능합니다.';
        infoText.style.color = '';
        return;
    }
    infoText.textContent = files.map(function (file) {
        return file.name + ' (' + formatFileSize(file.size) + ')';
    }).join(', ');
    infoText.style.color = '#c9a84c';
}

function renderFilePreview(input) {
    var previewBox = input.closest('.form-group') && input.closest('.form-group').querySelector('[data-file-preview]');
    if (!previewBox) return;

    var files = Array.from(input.files || []);
    previewBox.innerHTML = '';
    if (!files.length) {
        previewBox.style.display = 'none';
        return;
    }

    previewBox.style.display = 'grid';
    files.forEach(function (file) {
        var item = document.createElement('div');
        item.className = 'board-file-preview-item';

        if (file.type && file.type.startsWith('image/')) {
            var image = document.createElement('img');
            image.className = 'board-file-preview-thumb';
            image.alt = file.name;
            image.src = URL.createObjectURL(file);
            image.onload = function () {
                URL.revokeObjectURL(image.src);
            };
            item.appendChild(image);
        }

        var name = document.createElement('div');
        name.className = 'board-file-preview-name';
        name.textContent = file.name;

        var meta = document.createElement('div');
        meta.className = 'board-file-preview-meta';
        meta.textContent = formatFileSize(file.size);

        item.appendChild(name);
        item.appendChild(meta);
        previewBox.appendChild(item);
    });
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

window.mountBoardEditorControls = function (selector) {
    if (!window.jQuery || !$.fn || !$.fn.summernote) return;

    var $editor = $(selector);
    if (!$editor.length) return;

    var noteEditor = $editor.next('.note-editor')[0];
    if (!noteEditor || noteEditor.querySelector('.board-editor-controls')) return;

    var toolbar = noteEditor.querySelector('.note-toolbar');
    if (!toolbar) return;
    var selectedImage = null;

    var controls = document.createElement('div');
    controls.className = 'board-editor-controls';
    controls.innerHTML = [
        '<label class="board-editor-control">',
        '<span>크기</span>',
        '<select data-editor-font-size>',
        '<option value="12">12px</option>',
        '<option value="14">14px</option>',
        '<option value="16" selected>16px</option>',
        '<option value="18">18px</option>',
        '<option value="24">24px</option>',
        '<option value="32">32px</option>',
        '<option value="48">48px</option>',
        '</select>',
        '</label>',
        '<label class="board-editor-control">',
        '<span>글자색</span>',
        '<input type="color" data-editor-fore-color value="#ffffff">',
        '</label>',
        '<label class="board-editor-control">',
        '<span>배경색</span>',
        '<input type="color" data-editor-back-color value="#ffeb3b">',
        '</label>',
        '<div class="board-editor-align-group">',
        '<button type="button" class="board-editor-action" data-editor-align="justifyLeft">좌</button>',
        '<button type="button" class="board-editor-action" data-editor-align="justifyCenter">중</button>',
        '<button type="button" class="board-editor-action" data-editor-align="justifyRight">우</button>',
        '<button type="button" class="board-editor-action" data-editor-align="justifyFull">양</button>',
        '</div>'
    ].join('');

    toolbar.insertAdjacentElement('afterend', controls);

    var editable = noteEditor.querySelector('.note-editable');
    if (editable) {
        ['mouseup', 'keyup', 'mouseout', 'touchend'].forEach(function (eventName) {
            editable.addEventListener(eventName, function () {
                saveEditorRange($editor);
            });
        });

        editable.addEventListener('click', function (event) {
            selectedImage = event.target && event.target.tagName === 'IMG' ? event.target : null;
        });
    }

    var fontSizeSelect = controls.querySelector('[data-editor-font-size]');
    var foreColorInput = controls.querySelector('[data-editor-fore-color]');
    var backColorInput = controls.querySelector('[data-editor-back-color]');
    var alignButtons = controls.querySelectorAll('[data-editor-align]');

    if (fontSizeSelect) {
        fontSizeSelect.addEventListener('change', function () {
            applyEditorCommand($editor, function () {
                $editor.summernote('fontSize', parseInt(fontSizeSelect.value, 10));
            });
        });
    }

    if (foreColorInput) {
        foreColorInput.addEventListener('input', function () {
            applyEditorCommand($editor, function () {
                $editor.summernote('foreColor', foreColorInput.value);
            });
        });
    }

    if (backColorInput) {
        backColorInput.addEventListener('input', function () {
            applyEditorCommand($editor, function () {
                $editor.summernote('backColor', backColorInput.value);
            });
        });
    }

    alignButtons.forEach(function (button) {
        button.addEventListener('click', function () {
            alignButtons.forEach(function (item) {
                item.classList.toggle('is-active', item === button);
            });

            if (selectedImage && editable && editable.contains(selectedImage)) {
                applyEditorImageAlignment(selectedImage, button.getAttribute('data-editor-align'));
                return;
            }

            applyEditorCommand($editor, function () {
                $editor.summernote(button.getAttribute('data-editor-align'));
            });
        });
    });
};

function saveEditorRange($editor) {
    try {
        $editor.summernote('editor.saveRange');
    } catch (error) {
        console.debug('Summernote range save skipped.', error);
    }
}

function applyEditorCommand($editor, command) {
    try {
        $editor.summernote('focus');
        $editor.summernote('editor.restoreRange');
        command();
        $editor.summernote('editor.saveRange');
        $editor.summernote('focus');
    } catch (error) {
        console.error('Editor command failed.', error);
    }
}

function applyEditorImageAlignment(image, alignCommand) {
    if (!image) return;

    image.classList.remove(
        'board-image-align-left',
        'board-image-align-center',
        'board-image-align-right'
    );

    if (alignCommand === 'justifyCenter') {
        image.classList.add('board-image-align-center');
        return;
    }

    if (alignCommand === 'justifyRight') {
        image.classList.add('board-image-align-right');
        return;
    }

    image.classList.add('board-image-align-left');
}
