package com.logos.navigator.ui.map

import android.content.Context
import com.logos.navigator.data.model.Room

/** Текст площадки на двух языках; при отсутствии английского локаля используется русский. */
data class OpenDoorsText(
    val ru: String,
    val en: String,
) {
    fun resolve(context: Context): String =
        if (context.resources.configuration.locales[0].language == "en") en else ru
}

data class OpenDoorsVenue(
    val roomNumber: String,
    val title: OpenDoorsText,
    val description: OpenDoorsText,
    val leader: OpenDoorsText,
    val audience: OpenDoorsText,
    val highlights: List<OpenDoorsText>,
)

/** Событийный каталог копирует проверенные карточки сайта и включается только в release. */
object OpenDoorsCatalog {
    val venues = listOf(
        OpenDoorsVenue(
            roomNumber = "138",
            title = OpenDoorsText("Кибербез разума", "Cybersecurity of the Mind"),
            description = OpenDoorsText(
                "Площадка о критическом мышлении и цифровой гигиене: как отличать факты от манипуляций, проверять источники, защищать личные данные и безопасно пользоваться сервисами.",
                "Explore critical thinking and digital hygiene: distinguish facts from manipulation, verify sources, protect personal data, and use online services safely.",
            ),
            leader = OpenDoorsText("Руководитель — Рыбыдак Матвей", "Led by Matvey Rybidak"),
            audience = OpenDoorsText("Для абитуриентов и гостей колледжа", "For prospective students and college visitors"),
            highlights = listOf(
                OpenDoorsText("Критическое мышление", "Critical thinking"),
                OpenDoorsText("Цифровая гигиена", "Digital hygiene"),
                OpenDoorsText("Безопасность в сети", "Online safety"),
            ),
        ),
        OpenDoorsVenue(
            roomNumber = "148",
            title = OpenDoorsText("Игра «Информационная безопасность»", "Information Security Game"),
            description = OpenDoorsText(
                "Командная игра: участники ищут фишинговые письма, поддельные ссылки и небезопасные сценарии, учатся замечать уловки и правильно реагировать на них.",
                "A team game: spot phishing emails, fake links, and unsafe scenarios, then learn to recognize tricks and respond safely.",
            ),
            leader = OpenDoorsText("Ведущие — студенты групп 0-2", "Hosted by students from group 0-2"),
            audience = OpenDoorsText("Командная игра · 10–20 минут", "Team activity · 10–20 minutes"),
            highlights = listOf(
                OpenDoorsText("Фишинг", "Phishing"),
                OpenDoorsText("Командная работа", "Teamwork"),
                OpenDoorsText("Практика на примерах", "Hands-on examples"),
            ),
        ),
        OpenDoorsVenue(
            roomNumber = "114",
            title = OpenDoorsText("Мастер-класс «Сетевое администрирование»", "Network Administration Workshop"),
            description = OpenDoorsText(
                "На практике покажем, как работает коммутатор и собирается мини-схема сети: подключение кабелей, настройка портов и проверка связи между устройствами.",
                "See how a network switch works and build a small network: connect cables, configure ports, and test communication between devices.",
            ),
            leader = OpenDoorsText("Руководитель — Федеров Илья", "Led by Ilya Federov"),
            audience = OpenDoorsText("Для студентов групп 0-2", "For students from group 0-2"),
            highlights = listOf(
                OpenDoorsText("Коммутатор", "Network switch"),
                OpenDoorsText("Мини-схема сети", "Small network setup"),
                OpenDoorsText("Практическая настройка", "Hands-on configuration"),
            ),
        ),
        OpenDoorsVenue(
            roomNumber = "113",
            title = OpenDoorsText("Полёт на симуляторах БПЛА", "Drone Simulator Flight"),
            description = OpenDoorsText(
                "Знакомство с типами дронов и основами управления: тренажёрный центр с малой полётной зоной, инструктаж и пробный полёт на симуляторе.",
                "Learn about drone types and the basics of flight control: visit the training centre, get a briefing, and try a simulator flight.",
            ),
            leader = OpenDoorsText("Инструктор БПЛА и студенты группы Э-2", "Drone instructor and students from group E-2"),
            audience = OpenDoorsText("Пробный полёт под присмотром", "Supervised simulator flight"),
            highlights = listOf(
                OpenDoorsText("Типы дронов", "Drone types"),
                OpenDoorsText("Управление", "Flight control"),
                OpenDoorsText("Тренажёрный центр", "Training centre"),
            ),
        ),
        OpenDoorsVenue(
            roomNumber = "228",
            title = OpenDoorsText("Робототехника", "Robotics"),
            description = OpenDoorsText(
                "Демонстрация роботов и сборка: участники увидят, как устроены механизмы и программы, и попробуют собрать простую конструкцию.",
                "A robotics demonstration and build activity: see how mechanisms and programs work, then try assembling a simple model.",
            ),
            leader = OpenDoorsText("Студенты группы Р-2", "Hosted by students from group R-2"),
            audience = OpenDoorsText("Демонстрация и сборка", "Demonstration and assembly"),
            highlights = listOf(
                OpenDoorsText("Демонстрация роботов", "Robot demonstration"),
                OpenDoorsText("Сборка", "Assembly"),
                OpenDoorsText("Программирование", "Programming"),
            ),
        ),
    )

    fun roomsFor(rooms: List<Room>): List<Pair<OpenDoorsVenue, Room>> = venues.mapNotNull { venue ->
        rooms.firstOrNull { it.number == venue.roomNumber }?.let { room -> venue to room }
    }
}
