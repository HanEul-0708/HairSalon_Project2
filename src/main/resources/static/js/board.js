// board.js — 게시판 전용 자바스크립트
// =====================================================
// 이 스크립트는 게시판 목록, 작성/수정 페이지에서 공통으로 필요한
// 동작들을 정의합니다. 별도의 라이브러리에 의존하지 않고 순수
// 자바스크립트로 작성되었습니다.

document.addEventListener('DOMContentLoaded', function () {

    /*
     * 파일 입력: 선택한 파일 이름 + 크기 표시
     * --------------------------------------------
     * 게시글 작성/수정 페이지에서 첨부파일을 선택하면
     * 선택한 파일명과 파일 크기를 함께 보여준다.
     * 여러 파일을 선택했을 경우 각각 쉼표로 구분한다.
     *
     * 예시 출력)
     * 파일 1개 : 프로젝트기획서.pdf (1.2 MB)
     * 파일 3개 : 사진1.jpg (300 KB), 사진2.png (450 KB), 문서.docx (2.1 MB)
     */
    var fileInputs = document.querySelectorAll('input[type="file"]');

    fileInputs.forEach(function (input) {
        input.addEventListener('change', function () {
            var infoText = this.closest('.form-group') &&
                this.closest('.form-group').querySelector('.form-text');
            if (!infoText) return;

            var files = Array.from(this.files);

            if (files.length === 0) {
                /* 파일 선택 취소 시 원래 안내 문구로 복원 */
                infoText.textContent = '파일은 최대 10MB, 여러 개 선택 가능합니다.';
                infoText.style.color = '';
                return;
            }

            /* 파일명 + 크기 문자열 생성 */
            var fileInfoList = files.map(function (file) {
                return file.name + ' (' + formatFileSize(file.size) + ')';
            }).join(', ');

            infoText.textContent = fileInfoList;
            infoText.style.color = '#c9a84c'; /* 골드 색상으로 강조 */
        });
    });


    /*
     * 검색 입력: Enter 키로 폼 제출
     * --------------------------------------------
     * 게시판 목록 페이지에서 검색 입력창에서 Enter 키를 누르면
     * 명시적으로 폼을 제출한다. 일부 브라우저에서는 기본 제출을
     * 수행하지만, 일관된 UX를 위해 직접 호출한다.
     */
    var searchForm  = document.querySelector('.board-search-form');
    var searchInput = document.querySelector('.board-search-input');

    if (searchForm && searchInput) {
        searchInput.addEventListener('keypress', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                searchForm.submit();
            }
        });
    }

});


/*
 * formatFileSize — 파일 크기를 읽기 쉬운 문자열로 변환
 * --------------------------------------------
 * 바이트 단위 숫자를 받아서 B / KB / MB 단위로 변환해 반환한다.
 * FileUtil.java 의 formatFileSize() 와 동일한 로직 (서버 / 클라이언트 일관성 유지)
 *
 * 예시)
 * 500        → "500 B"
 * 10240      → "10.0 KB"
 * 1572864    → "1.5 MB"
 *
 * @param {number} bytes 파일 크기 (바이트)
 * @returns {string} 변환된 크기 문자열
 */
function formatFileSize(bytes) {
    if (bytes === 0)          return '0 B';
    if (bytes < 1024)         return bytes + ' B';
    if (bytes < 1024 * 1024)  return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}