# 🎵 MotionTune

Um player de música para dispositivos móveis que utiliza os sensores de movimento (acelerómetro e giroscopio) do telemóvel para controlar a reprodução das faixas.

## 📱 Sobre o Projeto
O MotionTune foi desenhado para proporcionar uma forma intuitiva de interação. Em vez de utilizar os botões tradicionais no ecrã táctil, o utilizador pode controlar a sua música com gestos físicos através do dispositivo móvel. É ideal para quando não se pode olhar diretamente para o ecrã. 

## 🛠️ Tecnologias Utilizadas
- **Linguagem:** Kotlin
- **Plataforma:** Android Nativo (Android Studio)
- **UI:** RecyclerView, Layouts XML
- **Sensores API:** Acesso ao Acelerómetro do dispositivo

## ✨ Funcionalidades
- **Controlos por Movimento:**
  - **Avançar Música (Next):** Inclinar o dispositivo 45º.
  - **Música Anterior (Previous):** Inclinar o dispositivo -45º.
  - **Modo Aleatório (Shuffle):** Abanar (*Shake*) o dispositivo.

## 🚀 Como Executar o Projeto
1. Faz o clone deste repositório:
   ```bash
   git clone https://github.com/TiagoCastro05/MotionTune.git
   ```
2. Abre a pasta do projeto no **Android Studio**.
3. Deixa o Gradle sincronizar todas as dependências.
4. Conecta o teu dispositivo Android via USB (com o modo *Developer / USB Debugging* ativado).
5. Compila e instala a aplicação no dispositivo.


*Nota: É estritamente recomendado testar num telemóvel físico real para garantir o correto funcionamento do acelerómetro, uma vez que emuladores não simulam movimento físico com facilidade e já ter ficheiros .mp3 já instalados no telemóvel.*
