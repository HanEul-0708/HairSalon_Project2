/*
 * member.js
 */

document.addEventListener("DOMContentLoaded", function () {
    setupDeleteConfirm();
    setupLoginValidation();
    setupSignupValidation();
    setupProfileValidation();
    setupPasswordChangeValidation();
});

function setupDeleteConfirm() {
    var deleteForms = document.querySelectorAll("form[data-member-delete]");
    deleteForms.forEach(function (form) {
        form.addEventListener("submit", function (e) {
            if (!confirm("정말 탈퇴하시겠습니까?")) {
                e.preventDefault();
            }
        });
    });
}

function setupLoginValidation() {
    var loginForm = document.querySelector(".login-box form");
    if (!loginForm) {
        return;
    }

    loginForm.addEventListener("submit", function (e) {
        var id = loginForm.querySelector("input[name='memberId']");
        var pw = loginForm.querySelector("input[name='password']");

        if (!id.value || !pw.value) {
            e.preventDefault();
            alert("아이디와 비밀번호를 입력해 주세요.");
        }
    });
}

function setFieldFeedback(container, selector, message, state) {
    var feedback = container.querySelector(selector);
    if (!feedback) {
        return;
    }

    feedback.textContent = message || "";
    feedback.classList.remove("text-danger", "text-success", "text-muted");

    if (!message) {
        return;
    }

    if (state === "success") {
        feedback.classList.add("text-success");
        return;
    }

    if (state === "pending") {
        feedback.classList.add("text-muted");
        return;
    }

    feedback.classList.add("text-danger");
}

function requestAvailability(url, value, onSuccess, onFailure) {
    fetch(url + encodeURIComponent(value), {
        method: "GET",
        headers: {
            "Accept": "application/json"
        }
    })
        .then(function (response) {
            if (!response.ok) {
                throw new Error("request failed");
            }
            return response.json();
        })
        .then(onSuccess)
        .catch(onFailure);
}

function fetchJson(url) {
    return fetch(url, {
        method: "GET",
        headers: {
            "Accept": "application/json"
        }
    }).then(function (response) {
        if (!response.ok) {
            throw new Error("request failed");
        }
        return response.json();
    });
}

function setupSignupValidation() {
    var signupForm = document.querySelector("form[data-signup-form]");
    if (!signupForm) {
        return;
    }

    var roleInputs = signupForm.querySelectorAll("[data-signup-role]");
    var memberIdInput = signupForm.querySelector("#memberId");
    var passwordInput = signupForm.querySelector("#password");
    var passwordConfirmInput = signupForm.querySelector("#passwordConfirm");
    var nameInput = signupForm.querySelector("#name");
    var phoneInput = signupForm.querySelector("#phone");
    var emailInput = signupForm.querySelector("#email");
    var citySelect = signupForm.querySelector("[data-signup-city]");
    var districtSelect = signupForm.querySelector("[data-signup-district]");
    var neighborhoodSelect = signupForm.querySelector("[data-signup-neighborhood]");
    var salonSelect = signupForm.querySelector("[data-signup-salon]");
    var designerSection = signupForm.querySelector("[data-designer-signup-section]");
    var designerNameHint = signupForm.querySelector("[data-designer-name-hint]");
    var submitButton = signupForm.querySelector("[data-signup-submit]");

    var state = {
        memberIdCheckedValue: "",
        memberIdAvailable: false,
        memberIdPending: false,
        phoneCheckedValue: "",
        phoneAvailable: false,
        phonePending: false,
        emailCheckedValue: "",
        emailAvailable: false,
        emailPending: false,
        memberIdTimer: null,
        phoneTimer: null,
        emailTimer: null,
        submitted: false,
        touched: {
            role: false,
            memberId: false,
            password: false,
            passwordConfirm: false,
            name: false,
            phone: false,
            email: false,
            salonId: false
        },
        userTypedName: nameInput.value || ""
    };

    function shouldShow(name) {
        return state.submitted || state.touched[name];
    }

    function currentRole() {
        var selected = signupForm.querySelector("[data-signup-role]:checked");
        return selected ? selected.value : "USER";
    }

    function isDesignerSignup() {
        return currentRole() === "DESIGNER";
    }

    function roleState() {
        var role = currentRole();
        if (role === "USER" || role === "DESIGNER") {
            return { valid: true, message: "", state: "success" };
        }
        return { valid: false, message: "회원 유형을 선택해 주세요.", state: "error" };
    }

    function memberIdState() {
        var value = memberIdInput.value.trim();
        var regex = /^[a-z0-9]{4,30}$/;

        if (!value) {
            return { valid: false, message: memberIdInput.dataset.requiredMessage, state: "error" };
        }
        if (!regex.test(value)) {
            return { valid: false, message: memberIdInput.dataset.invalidMessage, state: "error" };
        }
        if (state.memberIdPending) {
            return { valid: false, message: "아이디 중복 여부를 확인하는 중입니다.", state: "pending" };
        }
        if (state.memberIdCheckedValue === value && !state.memberIdAvailable) {
            return { valid: false, message: "이미 사용 중인 아이디입니다.", state: "error" };
        }
        if (state.memberIdCheckedValue === value && state.memberIdAvailable) {
            return { valid: true, message: "사용 가능한 아이디입니다.", state: "success" };
        }
        return { valid: false, message: "아이디 중복 여부를 자동으로 확인합니다.", state: "pending" };
    }

    function passwordState() {
        var value = passwordInput.value;

        if (!value) {
            return { valid: false, message: passwordInput.dataset.requiredMessage, state: "error" };
        }
        if (value.length < 8) {
            return { valid: false, message: passwordInput.dataset.invalidMessage, state: "error" };
        }
        return { valid: true, message: "사용 가능한 비밀번호 형식입니다.", state: "success" };
    }

    function passwordConfirmState() {
        var value = passwordConfirmInput.value;

        if (!value) {
            return { valid: false, message: passwordConfirmInput.dataset.requiredMessage, state: "error" };
        }
        if (value !== passwordInput.value) {
            return { valid: false, message: passwordConfirmInput.dataset.invalidMessage, state: "error" };
        }
        return { valid: true, message: "비밀번호가 일치합니다.", state: "success" };
    }

    function nameState() {
        var value = nameInput.value.trim();

        if (isDesignerSignup()) {
            if (!salonSelect.value) {
                return { valid: false, message: "미용실을 선택해 주세요.", state: "error" };
            }
            return { valid: true, message: "선택한 미용실 이름으로 계정명이 자동 설정됩니다.", state: "success" };
        }

        if (!value) {
            return { valid: false, message: nameInput.dataset.requiredMessage, state: "error" };
        }
        if (value.length > 50) {
            return { valid: false, message: nameInput.dataset.invalidMessage, state: "error" };
        }
        return { valid: true, message: "올바르게 입력되었습니다.", state: "success" };
    }

    function salonState() {
        if (!isDesignerSignup()) {
            return { valid: true, message: "", state: "success" };
        }

        if (!salonSelect.value) {
            return { valid: false, message: "가입할 미용실을 선택해 주세요.", state: "error" };
        }

        return { valid: true, message: "선택한 미용실 계정으로 가입합니다.", state: "success" };
    }

    function phoneState() {
        var value = phoneInput.value.trim();
        var regex = /^01[0-9]-?\d{3,4}-?\d{4}$/;

        if (!value) {
            return { valid: false, message: phoneInput.dataset.requiredMessage, state: "error" };
        }
        if (!regex.test(value)) {
            return { valid: false, message: phoneInput.dataset.invalidMessage, state: "error" };
        }
        if (state.phonePending) {
            return { valid: false, message: "전화번호 중복 여부를 확인하는 중입니다.", state: "pending" };
        }
        if (state.phoneCheckedValue === value && !state.phoneAvailable) {
            return { valid: false, message: "이미 사용 중인 전화번호입니다.", state: "error" };
        }
        if (state.phoneCheckedValue === value && state.phoneAvailable) {
            return { valid: true, message: "사용 가능한 전화번호입니다.", state: "success" };
        }
        return { valid: false, message: "전화번호 중복 여부를 자동으로 확인합니다.", state: "pending" };
    }

    function emailState() {
        var value = emailInput.value.trim();
        var regex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

        if (!value) {
            return { valid: false, message: emailInput.dataset.requiredMessage, state: "error" };
        }
        if (!regex.test(value)) {
            return { valid: false, message: emailInput.dataset.invalidMessage, state: "error" };
        }
        if (state.emailPending) {
            return { valid: false, message: "이메일 중복 여부를 확인하는 중입니다.", state: "pending" };
        }
        if (state.emailCheckedValue === value && !state.emailAvailable) {
            return { valid: false, message: "이미 사용 중인 이메일입니다.", state: "error" };
        }
        if (state.emailCheckedValue === value && state.emailAvailable) {
            return { valid: true, message: "사용 가능한 이메일입니다.", state: "success" };
        }
        return { valid: false, message: "이메일 중복 여부를 자동으로 확인합니다.", state: "pending" };
    }

    function renderField(name, result) {
        if (shouldShow(name)) {
            setFieldFeedback(signupForm, '[data-feedback-for="' + name + '"]', result.message, result.state);
        } else {
            setFieldFeedback(signupForm, '[data-feedback-for="' + name + '"]', "", "");
        }
    }

    function refreshUI() {
        var role = roleState();
        var memberId = memberIdState();
        var password = passwordState();
        var passwordConfirm = passwordConfirmState();
        var name = nameState();
        var phone = phoneState();
        var email = emailState();
        var salon = salonState();

        renderField("role", role);
        renderField("memberId", memberId);
        renderField("password", password);
        renderField("passwordConfirm", passwordConfirm);
        renderField("name", name);
        renderField("phone", phone);
        renderField("email", email);
        renderField("salonId", salon);

        if (designerSection) {
            designerSection.classList.toggle("d-none", !isDesignerSignup());
        }
        if (designerNameHint) {
            designerNameHint.classList.toggle("d-none", !isDesignerSignup());
        }
        if (nameInput) {
            nameInput.readOnly = isDesignerSignup();
        }

        submitButton.disabled = !(
            role.valid &&
            memberId.valid &&
            password.valid &&
            passwordConfirm.valid &&
            name.valid &&
            phone.valid &&
            email.valid &&
            salon.valid
        );
    }

    function fillSalonNameFromSelection() {
        if (!isDesignerSignup()) {
            nameInput.value = state.userTypedName;
            return;
        }

        var selectedOption = salonSelect.options[salonSelect.selectedIndex];
        if (!selectedOption || !salonSelect.value) {
            nameInput.value = "";
            return;
        }

        nameInput.value = selectedOption.text.split(" | ")[0].trim();
    }

    function rebuildOptions(select, items, placeholder, selectedValue, mapper) {
        if (!select) {
            return;
        }

        var options = ['<option value="">' + placeholder + '</option>'];
        items.forEach(function (item) {
            var mapped = mapper(item);
            var selected = String(mapped.value) === String(selectedValue || "") ? ' selected' : "";
            options.push('<option value="' + mapped.value + '"' + selected + '>' + mapped.label + '</option>');
        });
        select.innerHTML = options.join("");
    }

    function loadDistrictOptions(selectedValue) {
        var city = citySelect.value;
        rebuildOptions(districtSelect, [], "구 선택", "", function (item) {
            return { value: item, label: item };
        });
        rebuildOptions(neighborhoodSelect, [], "동 선택", "", function (item) {
            return { value: item, label: item };
        });
        rebuildOptions(salonSelect, [], "미용실 선택", "", function (item) {
            return { value: item.salonId, label: item.salonName + " | " + item.address };
        });

        if (!city) {
            fillSalonNameFromSelection();
            refreshUI();
            return Promise.resolve();
        }

        return fetchJson("/members/signup/district-options?city=" + encodeURIComponent(city))
            .then(function (items) {
                rebuildOptions(districtSelect, items, "구 선택", selectedValue, function (item) {
                    return { value: item, label: item };
                });
            })
            .catch(function () {
                setFieldFeedback(signupForm, '[data-feedback-for="salonId"]', "지역 정보를 불러오지 못했습니다.", "error");
            });
    }

    function loadNeighborhoodOptions(selectedValue) {
        var city = citySelect.value;
        var district = districtSelect.value;
        rebuildOptions(neighborhoodSelect, [], "동 선택", "", function (item) {
            return { value: item, label: item };
        });

        if (!city || !district) {
            return Promise.resolve();
        }

        return fetchJson(
            "/members/signup/neighborhood-options?city=" + encodeURIComponent(city) +
            "&district=" + encodeURIComponent(district)
        ).then(function (items) {
            rebuildOptions(neighborhoodSelect, items, "동 선택", selectedValue, function (item) {
                return { value: item, label: item };
            });
        }).catch(function () {
            setFieldFeedback(signupForm, '[data-feedback-for="salonId"]', "동 정보를 불러오지 못했습니다.", "error");
        });
    }

    function loadSalonOptions(selectedValue) {
        var params = [];

        if (citySelect.value) {
            params.push("city=" + encodeURIComponent(citySelect.value));
        }
        if (districtSelect.value) {
            params.push("district=" + encodeURIComponent(districtSelect.value));
        }
        if (neighborhoodSelect.value) {
            params.push("neighborhood=" + encodeURIComponent(neighborhoodSelect.value));
        }

        rebuildOptions(salonSelect, [], "미용실 선택", "", function (item) {
            return { value: item.salonId, label: item.salonName + " | " + item.address };
        });

        if (!citySelect.value || !districtSelect.value) {
            fillSalonNameFromSelection();
            refreshUI();
            return Promise.resolve();
        }

        return fetchJson("/members/signup/salon-options" + (params.length ? "?" + params.join("&") : ""))
            .then(function (items) {
                rebuildOptions(salonSelect, items, "미용실 선택", selectedValue, function (item) {
                    return {
                        value: item.salonId,
                        label: item.salonName + " | " + (item.address || "")
                    };
                });
                fillSalonNameFromSelection();
                refreshUI();
            })
            .catch(function () {
                setFieldFeedback(signupForm, '[data-feedback-for="salonId"]', "미용실 목록을 불러오지 못했습니다.", "error");
            });
    }

    function scheduleMemberIdCheck() {
        var value = memberIdInput.value.trim();
        var regex = /^[a-z0-9]{4,30}$/;

        clearTimeout(state.memberIdTimer);
        state.memberIdCheckedValue = "";
        state.memberIdAvailable = false;

        if (!value || !regex.test(value)) {
            state.memberIdPending = false;
            refreshUI();
            return;
        }

        state.memberIdPending = true;
        refreshUI();

        state.memberIdTimer = setTimeout(function () {
            requestAvailability(
                "/members/check-id?memberId=",
                value,
                function (data) {
                    if (memberIdInput.value.trim() !== value) {
                        return;
                    }

                    state.memberIdCheckedValue = value;
                    state.memberIdAvailable = !!data.available;
                    state.memberIdPending = false;
                    refreshUI();
                },
                function () {
                    if (memberIdInput.value.trim() !== value) {
                        return;
                    }

                    state.memberIdCheckedValue = "";
                    state.memberIdAvailable = false;
                    state.memberIdPending = false;
                    if (shouldShow("memberId")) {
                        setFieldFeedback(signupForm, '[data-feedback-for="memberId"]', "아이디 확인 중 오류가 발생했습니다.", "error");
                    }
                    refreshUI();
                }
            );
        }, 500);
    }

    function schedulePhoneCheck() {
        var value = phoneInput.value.trim();
        var regex = /^01[0-9]-?\d{3,4}-?\d{4}$/;

        clearTimeout(state.phoneTimer);
        state.phoneCheckedValue = "";
        state.phoneAvailable = false;

        if (!value || !regex.test(value)) {
            state.phonePending = false;
            refreshUI();
            return;
        }

        state.phonePending = true;
        refreshUI();

        state.phoneTimer = setTimeout(function () {
            requestAvailability(
                "/members/check-phone?phone=",
                value,
                function (data) {
                    if (phoneInput.value.trim() !== value) {
                        return;
                    }

                    state.phoneCheckedValue = value;
                    state.phoneAvailable = !!data.available;
                    state.phonePending = false;
                    refreshUI();
                },
                function () {
                    if (phoneInput.value.trim() !== value) {
                        return;
                    }

                    state.phoneCheckedValue = "";
                    state.phoneAvailable = false;
                    state.phonePending = false;
                    if (shouldShow("phone")) {
                        setFieldFeedback(signupForm, '[data-feedback-for="phone"]', "전화번호 확인 중 오류가 발생했습니다.", "error");
                    }
                    refreshUI();
                }
            );
        }, 500);
    }

    function scheduleEmailCheck() {
        var value = emailInput.value.trim();
        var regex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

        clearTimeout(state.emailTimer);
        state.emailCheckedValue = "";
        state.emailAvailable = false;

        if (!value || !regex.test(value)) {
            state.emailPending = false;
            refreshUI();
            return;
        }

        state.emailPending = true;
        refreshUI();

        state.emailTimer = setTimeout(function () {
            requestAvailability(
                "/members/check-email?email=",
                value,
                function (data) {
                    if (emailInput.value.trim() !== value) {
                        return;
                    }

                    state.emailCheckedValue = value;
                    state.emailAvailable = !!data.available;
                    state.emailPending = false;
                    refreshUI();
                },
                function () {
                    if (emailInput.value.trim() !== value) {
                        return;
                    }

                    state.emailCheckedValue = "";
                    state.emailAvailable = false;
                    state.emailPending = false;
                    if (shouldShow("email")) {
                        setFieldFeedback(signupForm, '[data-feedback-for="email"]', "이메일 확인 중 오류가 발생했습니다.", "error");
                    }
                    refreshUI();
                }
            );
        }, 500);
    }

    memberIdInput.addEventListener("input", function () {
        state.touched.memberId = true;
        scheduleMemberIdCheck();
    });
    roleInputs.forEach(function (input) {
        input.addEventListener("change", function () {
            state.touched.role = true;
            if (!isDesignerSignup()) {
                state.userTypedName = nameInput.value.trim();
            }
            if (isDesignerSignup()) {
                fillSalonNameFromSelection();
            } else {
                nameInput.value = state.userTypedName;
            }
            refreshUI();
        });
    });
    passwordInput.addEventListener("input", function () {
        state.touched.password = true;
        refreshUI();
    });
    passwordConfirmInput.addEventListener("input", function () {
        state.touched.passwordConfirm = true;
        refreshUI();
    });
    nameInput.addEventListener("input", function () {
        state.touched.name = true;
        if (!isDesignerSignup()) {
            state.userTypedName = nameInput.value;
        }
        refreshUI();
    });
    phoneInput.addEventListener("input", function () {
        state.touched.phone = true;
        schedulePhoneCheck();
    });
    emailInput.addEventListener("input", function () {
        state.touched.email = true;
        scheduleEmailCheck();
    });
    if (citySelect) {
        citySelect.addEventListener("change", function () {
            state.touched.salonId = true;
            loadDistrictOptions("").then(function () {
                fillSalonNameFromSelection();
                refreshUI();
            });
        });
    }
    if (districtSelect) {
        districtSelect.addEventListener("change", function () {
            state.touched.salonId = true;
            loadNeighborhoodOptions("").then(function () {
                return loadSalonOptions("");
            }).then(function () {
                fillSalonNameFromSelection();
                refreshUI();
            });
        });
    }
    if (neighborhoodSelect) {
        neighborhoodSelect.addEventListener("change", function () {
            state.touched.salonId = true;
            loadSalonOptions("").then(function () {
                fillSalonNameFromSelection();
                refreshUI();
            });
        });
    }
    if (salonSelect) {
        salonSelect.addEventListener("change", function () {
            state.touched.salonId = true;
            fillSalonNameFromSelection();
            refreshUI();
        });
    }

    signupForm.addEventListener("submit", function (e) {
        state.submitted = true;
        if (isDesignerSignup()) {
            fillSalonNameFromSelection();
        }
        refreshUI();
        if (submitButton.disabled) {
            e.preventDefault();
        }
    });

    fillSalonNameFromSelection();
    refreshUI();
}

function setupProfileValidation() {
    var profileForm = document.querySelector("form[data-profile-form]");
    if (!profileForm) {
        return;
    }

    var nameInput = profileForm.querySelector("#updateName");
    var phoneInput = profileForm.querySelector("#updatePhone");
    var emailInput = profileForm.querySelector("#updateEmail");
    var submitButton = profileForm.querySelector("[data-profile-submit]");

    var state = {
        phoneCheckedValue: "",
        phoneAvailable: false,
        phonePending: false,
        emailCheckedValue: "",
        emailAvailable: false,
        emailPending: false,
        phoneTimer: null,
        emailTimer: null,
        submitted: false,
        touched: {
            name: false,
            phone: false,
            email: false
        }
    };

    function shouldShow(name) {
        return state.submitted || state.touched[name];
    }

    function nameState() {
        var value = nameInput.value.trim();

        if (!value) {
            return { valid: false, message: nameInput.dataset.requiredMessage, state: "error" };
        }
        if (value.length > 50) {
            return { valid: false, message: nameInput.dataset.invalidMessage, state: "error" };
        }
        return { valid: true, message: "올바르게 입력되었습니다.", state: "success" };
    }

    function phoneState() {
        var value = phoneInput.value.trim();
        var regex = /^01[0-9]-?\d{3,4}-?\d{4}$/;
        var currentPhone = (phoneInput.dataset.currentPhone || "").trim();

        if (!value) {
            return { valid: false, message: phoneInput.dataset.requiredMessage, state: "error" };
        }
        if (!regex.test(value)) {
            return { valid: false, message: phoneInput.dataset.invalidMessage, state: "error" };
        }
        if (value === currentPhone) {
            return { valid: true, message: "현재 사용 중인 전화번호입니다.", state: "success" };
        }
        if (state.phonePending) {
            return { valid: false, message: "전화번호 중복 여부를 확인하는 중입니다.", state: "pending" };
        }
        if (state.phoneCheckedValue === value && !state.phoneAvailable) {
            return { valid: false, message: "이미 사용 중인 전화번호입니다.", state: "error" };
        }
        if (state.phoneCheckedValue === value && state.phoneAvailable) {
            return { valid: true, message: "사용 가능한 전화번호입니다.", state: "success" };
        }
        return { valid: false, message: "전화번호 중복 여부를 자동으로 확인합니다.", state: "pending" };
    }

    function emailState() {
        var value = emailInput.value.trim();
        var regex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        var currentEmail = (emailInput.dataset.currentEmail || "").trim();

        if (!value) {
            return { valid: false, message: emailInput.dataset.requiredMessage, state: "error" };
        }
        if (!regex.test(value)) {
            return { valid: false, message: emailInput.dataset.invalidMessage, state: "error" };
        }
        if (value === currentEmail) {
            return { valid: true, message: "현재 사용 중인 이메일입니다.", state: "success" };
        }
        if (state.emailPending) {
            return { valid: false, message: "이메일 중복 여부를 확인하는 중입니다.", state: "pending" };
        }
        if (state.emailCheckedValue === value && !state.emailAvailable) {
            return { valid: false, message: "이미 사용 중인 이메일입니다.", state: "error" };
        }
        if (state.emailCheckedValue === value && state.emailAvailable) {
            return { valid: true, message: "사용 가능한 이메일입니다.", state: "success" };
        }
        return { valid: false, message: "이메일 중복 여부를 자동으로 확인합니다.", state: "pending" };
    }

    function renderField(name, result) {
        if (shouldShow(name)) {
            setFieldFeedback(profileForm, '[data-profile-feedback-for="' + name + '"]', result.message, result.state);
        } else {
            setFieldFeedback(profileForm, '[data-profile-feedback-for="' + name + '"]', "", "");
        }
    }

    function refreshUI() {
        var name = nameState();
        var phone = phoneState();
        var email = emailState();

        renderField("name", name);
        renderField("phone", phone);
        renderField("email", email);

        submitButton.disabled = !(name.valid && phone.valid && email.valid);
    }

    function schedulePhoneCheck() {
        var value = phoneInput.value.trim();
        var regex = /^01[0-9]-?\d{3,4}-?\d{4}$/;
        var currentPhone = (phoneInput.dataset.currentPhone || "").trim();

        clearTimeout(state.phoneTimer);
        state.phoneCheckedValue = "";
        state.phoneAvailable = false;

        if (!value || !regex.test(value) || value === currentPhone) {
            state.phonePending = false;
            refreshUI();
            return;
        }

        state.phonePending = true;
        refreshUI();

        state.phoneTimer = setTimeout(function () {
            requestAvailability(
                "/members/me/check-phone?phone=",
                value,
                function (data) {
                    if (phoneInput.value.trim() !== value) {
                        return;
                    }

                    state.phoneCheckedValue = value;
                    state.phoneAvailable = !!data.available;
                    state.phonePending = false;
                    refreshUI();
                },
                function () {
                    if (phoneInput.value.trim() !== value) {
                        return;
                    }

                    state.phoneCheckedValue = "";
                    state.phoneAvailable = false;
                    state.phonePending = false;
                    if (shouldShow("phone")) {
                        setFieldFeedback(profileForm, '[data-profile-feedback-for="phone"]', "전화번호 확인 중 오류가 발생했습니다.", "error");
                    }
                    refreshUI();
                }
            );
        }, 500);
    }

    function scheduleEmailCheck() {
        var value = emailInput.value.trim();
        var regex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        var currentEmail = (emailInput.dataset.currentEmail || "").trim();

        clearTimeout(state.emailTimer);
        state.emailCheckedValue = "";
        state.emailAvailable = false;

        if (!value || !regex.test(value) || value === currentEmail) {
            state.emailPending = false;
            refreshUI();
            return;
        }

        state.emailPending = true;
        refreshUI();

        state.emailTimer = setTimeout(function () {
            requestAvailability(
                "/members/me/check-email?email=",
                value,
                function (data) {
                    if (emailInput.value.trim() !== value) {
                        return;
                    }

                    state.emailCheckedValue = value;
                    state.emailAvailable = !!data.available;
                    state.emailPending = false;
                    refreshUI();
                },
                function () {
                    if (emailInput.value.trim() !== value) {
                        return;
                    }

                    state.emailCheckedValue = "";
                    state.emailAvailable = false;
                    state.emailPending = false;
                    if (shouldShow("email")) {
                        setFieldFeedback(profileForm, '[data-profile-feedback-for="email"]', "이메일 확인 중 오류가 발생했습니다.", "error");
                    }
                    refreshUI();
                }
            );
        }, 500);
    }

    nameInput.addEventListener("input", function () {
        state.touched.name = true;
        refreshUI();
    });
    phoneInput.addEventListener("input", function () {
        state.touched.phone = true;
        schedulePhoneCheck();
    });
    emailInput.addEventListener("input", function () {
        state.touched.email = true;
        scheduleEmailCheck();
    });

    profileForm.addEventListener("submit", function (e) {
        state.submitted = true;
        refreshUI();
        if (submitButton.disabled) {
            e.preventDefault();
        }
    });

    refreshUI();
}

function setupPasswordChangeValidation() {
    var passwordForm = document.querySelector("form[action='/members/me/password']");
    if (!passwordForm) {
        return;
    }

    var newPasswordInput = passwordForm.querySelector("#newPassword");
    var newPasswordConfirmInput = passwordForm.querySelector("#newPasswordConfirm");
    var submitButton = passwordForm.querySelector("[data-password-submit]");

    if (!newPasswordInput || !newPasswordConfirmInput || !submitButton) {
        return;
    }

    var state = {
        submitted: false,
        touched: {
            newPassword: false,
            newPasswordConfirm: false
        }
    };

    function shouldShow(name) {
        return state.submitted || state.touched[name];
    }

    function newPasswordState() {
        var value = newPasswordInput.value;

        if (!value) {
            return { valid: false, message: newPasswordInput.dataset.requiredMessage, state: "error" };
        }
        if (value.length < 8) {
            return { valid: false, message: newPasswordInput.dataset.invalidMessage, state: "error" };
        }
        return { valid: true, message: "사용 가능한 비밀번호 형식입니다.", state: "success" };
    }

    function newPasswordConfirmState() {
        var value = newPasswordConfirmInput.value;

        if (!value) {
            return { valid: false, message: newPasswordConfirmInput.dataset.requiredMessage, state: "error" };
        }
        if (value !== newPasswordInput.value) {
            return { valid: false, message: newPasswordConfirmInput.dataset.invalidMessage, state: "error" };
        }
        return { valid: true, message: "새 비밀번호가 일치합니다.", state: "success" };
    }

    function refreshUI() {
        var password = newPasswordState();
        var confirmPassword = newPasswordConfirmState();

        if (shouldShow("newPassword")) {
            setFieldFeedback(passwordForm, '[data-password-feedback-for="newPassword"]', password.message, password.state);
        } else {
            setFieldFeedback(passwordForm, '[data-password-feedback-for="newPassword"]', "", "");
        }

        if (shouldShow("newPasswordConfirm")) {
            setFieldFeedback(passwordForm, '[data-password-feedback-for="newPasswordConfirm"]', confirmPassword.message, confirmPassword.state);
        } else {
            setFieldFeedback(passwordForm, '[data-password-feedback-for="newPasswordConfirm"]', "", "");
        }

        submitButton.disabled = !(password.valid && confirmPassword.valid);
    }

    newPasswordInput.addEventListener("input", function () {
        state.touched.newPassword = true;
        refreshUI();
    });

    newPasswordConfirmInput.addEventListener("input", function () {
        state.touched.newPasswordConfirm = true;
        refreshUI();
    });

    passwordForm.addEventListener("submit", function (e) {
        state.submitted = true;
        refreshUI();
        if (submitButton.disabled) {
            e.preventDefault();
        }
    });

    refreshUI();
}
